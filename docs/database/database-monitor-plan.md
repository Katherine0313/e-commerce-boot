# 数据库监控模块设计方案

> 目标：在现有「系统监控」下补一块**数据库监控**，能回答三个问题：
> 哪条 SQL 慢、慢在哪、什么时候开始慢的。
> 本文只做方案说明，不含代码实现。

---

## 1. 结论先行

| 事项 | 结论 |
|---|---|
| 菜单位置 | 挂在现有「系统监控」（id=900）下，新增 `数据库监控`（id=908），排序第 5 |
| 后端形态 | **不新建服务**，采集放 `common`，查询接口放 `system-service`（网关 `/api/admin/monitor/**` 已经指向它） |
| 采集方式 | `common` 里加一个 **MyBatis Interceptor**，只记录超过阈值的 SQL，不引 p6spy、不引 Druid |
| 存储 | MySQL 新表 `admin_db_slow_sql`，保留 7 天，每天凌晨分批清理 |
| 数据来源 | 慢 SQL（拦截器）+ Hikari 连接池（actuator 已有指标）+ MySQL 全局状态（`SHOW GLOBAL STATUS`）+ 表容量（`information_schema`） |
| 权限码 | `monitor:db:list`（查看）、`monitor:db:clear`（清空历史） |
| 采集开销 | 每服务每分钟最多入库 60 条，异步写入；队列满时丢弃的是**监控记录**，业务请求照常执行、响应时间不受影响；配置可一键关闭 |
| 最大风险 | 采集本身要写库，如果阈值/限流配错，会在 DB 慢的时候雪上加霜。所以默认**只记慢的、只在内存排队、失败静默** |

**边界一句话**：数据库管理（`/system/database`）管"库长什么样、能不能备份"；数据库监控管"库跑得怎么样、哪条 SQL 拖后腿"。

---

## 2. 现状：为什么现在看不到

| 能力 | 现状 | 依据 |
|---|---|---|
| 监控菜单 | 只有服务监控 / 在线用户 / 缓存监控 / 定时任务，**没有数据库** | `docs/sql/20261105_system_monitor_menu.sql:14-17` |
| 服务监控 | 主机（OSHI）+ JVM + Docker 容器 + Redis/Nacos 探活，全是进程/机器维度 | `ServerMonitorService.java:83-95` |
| 服务指标 | 聚合各服务 actuator：堆、CPU、线程、GC、**HTTP 接口**耗时 | `ServiceMetricsService.java:79-100` |
| SQL 日志 | MyBatis 未配 `log-impl`，生产 `SQL_DEBUG=INFO`，**不打 SQL** | `product-service/application.yml:76-80,89-91`；`deploy/.env.example:50` |
| 慢 SQL 归档 | 无。没有 p6spy / Druid / 自定义拦截器 / 慢查询表 | 全仓检索结果为空 |
| 唯一跟耗时有关的 | `admin_operation_log.cost_ms`，但那是**整个接口方法**耗时，且只覆盖加了 `@OperationLog` 的写操作 | `OperationLogAspect.java:52-65` |
| MySQL 慢查询日志 | 仓库无配置，宝塔默认一般关闭，需上服务器确认 | — |

**结论**：现在接口耗时可以看到"哪个接口慢"，但看不到"哪条 SQL 慢"，也没有历史留存，事后无法复盘。

---

## 3. 范围

### 3.1 要做（一期）

| 模块 | 内容 |
|---|---|
| 慢 SQL 明细 | 时间、服务名、Mapper 方法、SQL 类型、归一化 SQL、耗时、影响行数 |
| 慢 SQL 聚合 | 按 SQL 指纹分组：执行次数、平均/最大耗时、最近出现时间、涉及服务 |
| 慢 SQL 趋势 | 按小时统计条数 + 平均耗时（折线图） |
| 连接池状态 | 各服务 Hikari：活跃/空闲/等待/上限，等待获取连接的线程数 |
| DB 总览 | 当前连接数 / 上限、QPS、慢查询累计、InnoDB 缓冲池命中率、库表数量、数据总容量 |
| 表容量 Top | 按 `data_length + index_length` 排序前 10 |
| 清理 | 每日自动删 7 天前记录；页面支持手动清空（需权限 + 留操作日志） |

### 3.2 明确不做（一期）

- 不接 Prometheus / SkyWalking / 任何 APM 组件；
- **不做全量 SQL 采样**，只记超过阈值的（否则表会被打爆）；
- **不记录绑定参数的实际值**（手机号、地址等敏感数据，且体积不可控），只存 `?` 占位；
- 不在页面上跑 `EXPLAIN` / `SHOW PROCESSLIST`（有锁风险，且暴露连接信息）；
- 不做长期趋势存储（>7 天），需要长期数据请上监控平台；
- 不改 MySQL 的 `slow_query_log` 配置（属运维动作，与本模块解耦，见 §13）。

---

## 4. 总体架构

```
业务服务 (product/order/auth/user/aftersale/system)
    │
    │  MyBatis 执行 SQL
    ▼
common: SqlMonitorInterceptor  ── 耗时 >= 阈值 ──►  内存队列（有界）
                                                      │  独立线程池（满则丢弃）
                                                      ▼
                                            admin_db_slow_sql 表
                                                      ▲
                                                      │ 查询
system-service: DatabaseMonitorService ◄──────────────┘
    │  + Hikari MXBean（本服务）
    │  + 各服务 /actuator/metrics/hikaricp.*（已有 ServiceMetricsService 框架）
    │  + SHOW GLOBAL STATUS / information_schema
    ▼
/api/admin/monitor/database/**
    ▲
电商后台 /monitor/database 页面
```

关键点：**采集在 common（所有服务共享），展示在 system-service（单点）**。所有服务连的是同一个库 `e_commerce_db`，所以慢 SQL 天然汇总到一张表。

---

## 5. 采集端设计（common）

### 5.1 为什么是 MyBatis 拦截器，而不是别的

| 方案 | 不选的理由 |
|---|---|
| p6spy | 要多引一个 jar + 改 JDBC URL，等于所有服务的数据源配置都得动；输出是日志文件，还得再解析入库 |
| Druid StatFilter | 要换连接池（现在是 Hikari），改动面太大 |
| MySQL `slow_query_log` | 只能拿到服务端视角，**不知道是哪个 Java 方法/哪条 Mapper 发出来的**，排查时还得猜 |
| AOP 包 Service 方法 | 只能到"这个方法慢"，一个方法里可能十几条 SQL，定位不到具体语句 |
| **MyBatis Interceptor** | 在 JDBC 调用点测量，能同时拿到 `MappedStatement`（Mapper 方法）+ 真实 SQL + 耗时 + 影响行数，零依赖、零配置改动 |

### 5.2 拦截点与采集内容

拦截 `Executor` 的 3 个方法：

```
@Intercepts({
  @Signature(type = Executor.class, method = "query",
             args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
  @Signature(type = Executor.class, method = "query",
             args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                     CacheKey.class, BoundSql.class}),
  @Signature(type = Executor.class, method = "update",
             args = {MappedStatement.class, Object.class})
})
```

> 两个 `query` 重载都要拦：分页/缓存的调用链会走带 `CacheKey` + `BoundSql` 的那个，只拦第一个会漏。

每次采集的字段：

| 字段 | 取值方式 |
|---|---|
| `service_name` | 启动时从 `spring.application.name` 读一次，如 `product-service` |
| `mapper_method` | `MappedStatement.getId()`，如 `ecommerce.product.mapper.ProductMapper.selectList` |
| `sql_type` | `SqlCommandType`，取 SELECT / INSERT / UPDATE / DELETE |
| `sql_text` | `BoundSql.getSql()` → 空白归一化 → 截断 2000 字符（**保留 `?`，不填参数值**） |
| `sql_fingerprint` | `MD5(去字面量后的 SQL)`，用于聚合相同语句 |
| `cost_ms` | `System.nanoTime()` 差值 / 1e6，向上取整 |
| `row_count` | query 取 `List.size()`，update 取返回值 |

**口径说明**（页面要注明）：这里测的是 **Java 侧看到的 SQL 执行耗时**，含网络往返 + DB 执行，不含 `Connection` 获取等待（那部分看连接池 `pending`），也不含结果集转对象的后续处理。

### 5.3 SQL 归一化与指纹

同一句 SQL 因为参数不同、`IN` 列表长度不同，会被拆成很多条，聚合页就没意义了。规整规则：

1. 把连续空白/换行压成一个空格，去掉首尾空白；
2. 字符串字面量 `'...'` → `?`；
3. 数字字面量 `123`、`12.5` → `?`；
4. `IN (?, ?, ?)` 这种变长列表 → `IN (...)`；
5. 对结果做 MD5（32 位）存 `sql_fingerprint`。

这样"按商品 ID 查 SKU"无论参数是什么，都归到同一个指纹下，Top 榜才有意义。

### 5.4 写入策略（这一节决定模块会不会帮倒忙）

1. **只记慢的**：`cost_ms < slow-threshold-ms`（默认 300ms）直接 return，采集逻辑只做一次 `nanoTime` 差值，几乎零成本。
2. **有界队列 + 独立线程池**：仿照 `LogAsyncConfig` / `JdbcOperationLogRecorder` 的既有做法，新建 `sqlMonitorExecutor`；队列声明容量（如 1000），`RejectedExecutionHandler` 用**丢弃 + 计数**。

   > **丢弃的是监控记录，不是业务请求。** 业务 SQL 在请求线程里执行完就直接返回了，从头到尾不进这个队列；队列满只会让"这条慢 SQL 没被记下来"，用户侧不报错、不阻塞、不增加响应时间。
   >
   > 与操作日志的取舍刻意不同：`LogAsyncConfig` 用的是 `CallerRunsPolicy`（池满让调用线程自己写，宁可阻塞也要保住审计日志）；慢 SQL 是**采样数据**，为它阻塞业务线程是本末倒置，所以这里选丢弃，并把丢弃条数显示在总览页。

3. **限流**：每个服务实例每分钟最多入库 `max-per-minute`（默认 60）条，本地 `AtomicLong` + 分钟窗口计数；超了丢弃并累计"丢弃数"，在总览里可见。
4. **递归防护**：写入走 **JdbcTemplate**（不走 MyBatis），天然绕过拦截器；再加一个 `ThreadLocal<Boolean>` 标志位兜底，防止将来改成 Mapper 写入时自激。
5. **异常静默**：写库失败只 `log.warn`，与操作日志的语义一致——监控不能影响业务。
6. **开关**：`monitor.sql.enabled=false` 时整个拦截器不装配。

### 5.5 依赖与条件装配

`common` 目前**没有** MyBatis 依赖（见 `common/pom.xml`），而 `file-service` 也没引 MyBatis。所以：

- `common/pom.xml` 加 `mybatis`（或 `mybatis-spring-boot-starter`）依赖，**`<optional>true</optional>`**；
- 拦截器用 `@Configuration` + `@Bean` + `@ConditionalOnClass(Interceptor.class)` + `@ConditionalOnProperty(prefix="monitor.sql", name="enabled", matchIfMissing=true)` 装配；
- `mybatis-spring-boot-starter` 会自动把容器里所有 `Interceptor` Bean 注册进 `Configuration`，**各业务服务启动类一行都不用改**（它们已经是 `scanBasePackages = "ecommerce"`，如 `ProductServiceApplication.java:10`）；
- 没有 MyBatis 的服务（file-service）不满足 `@ConditionalOnClass`，不会加载；网关不引 `common`（见 `gateway/pom.xml:51`），完全不受影响；
- **例外要单独验证**：`user-service` 用的是 `mybatis-plus-spring-boot4-starter`（`user-service/pom.xml:52`），不是原生 starter。MyBatis-Plus 的自动配置一般也会注入容器里的 `Interceptor` Bean，但版本差异较大，P1 阶段必须实测 user-service 能否采集；不生效就先接受"user-service 暂不采集"，不要把方案卡在这里。

---

## 6. 存储设计

### 6.1 建表

```sql
CREATE TABLE IF NOT EXISTS `admin_db_slow_sql` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `service_name` varchar(64) NOT NULL COMMENT '服务名，如 product-service',
  `mapper_method` varchar(255) NOT NULL COMMENT 'MappedStatement id，定位到 Mapper 方法',
  `sql_type` varchar(16) DEFAULT NULL COMMENT 'SELECT/INSERT/UPDATE/DELETE',
  `sql_fingerprint` char(32) NOT NULL COMMENT '归一化 SQL 的 MD5，用于聚合',
  `sql_text` text COMMENT '归一化 SQL，截断 2000 字符，不含参数值',
  `cost_ms` int NOT NULL COMMENT 'SQL 执行耗时（毫秒）',
  `row_count` int DEFAULT NULL COMMENT '返回/影响行数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_fingerprint` (`sql_fingerprint`),
  KEY `idx_service_created` (`service_name`, `created_at`),
  KEY `idx_cost` (`cost_ms`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='慢 SQL 监控记录';
```

设计说明：

- 用 `int` 存 `cost_ms` 而不是 `bigint`：4 字节，够用；
- `sql_text` 用 `text`（不建索引），聚合靠 `sql_fingerprint`；
- **不存参数值**，`mapper_method` 已足够定位代码位置；
- 表名带 `admin_` 前缀，与 `admin_operation_log` / `admin_login_log` / `admin_monitor_task` 保持一致。

### 6.2 容量估算

按每服务每分钟 60 条上限、实际日均 2000 条、单行约 800B 估算：

- 每天 ≈ 1.6 MB，7 天 ≈ 11 MB；
- 加上 4 个索引，控制在 30 MB 以内，对现有库可忽略。

### 6.3 清理

新增 `DatabaseSlowSqlCleanupTask`，放在 `system-service`（只有一个实例跑，避免多服务重复删）：

- 仿照 `LogCleanupTask`：`@MonitoredTask(name = "dbSlowSqlCleanup", cron = "0 20 3 * * ?")`，**分批删**（每批 1000 行，最多 200 批）；
- 必须同时提供 `DatabaseSlowSqlCleanupTaskRunner implements MonitoredTaskRunner`，并保证 **三处一致**：`taskName()` == `@MonitoredTask.name()` == `admin_monitor_task.task_name`，否则调度器扫不到（`DynamicTaskScheduler.java:110-115` 是按"本服务有没有同名 Runner"筛的）；
- `task_group = 'system-service'` **只影响手动执行**：页面点"执行"时按它去 Nacos 找实例转发；自动调度不看这个字段。两者别混；
- system-service 已开 `monitor.dynamic-task.enabled: true`（`system-service/application.yml:68-70`），不用额外配置；
- 阈值由 `monitor.sql.retain-days`（默认 7）控制；
- 页面「清空历史」用同一个删除方法，但走 `DELETE ... WHERE created_at < ?`，不是 `TRUNCATE`（避免锁表）。

SQL 注册脚本：`docs/sql/20261110_db_monitor.sql`。

---

## 7. 服务端接口（system-service）

新增 `DatabaseMonitorController`，路径 `/api/admin/monitor/database`，网关现有路由 `/api/admin/monitor/**` 已覆盖，**不用改网关**。

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/overview` | `monitor:db:list` | 指标卡：连接数/上限、QPS、今日慢 SQL 数、命中率、库表数、总容量、采集丢弃数 |
| GET | `/slow-sqls` | `monitor:db:list` | 慢 SQL 明细分页：`service` / `minCost` / `hours` / `page` / `pageSize` |
| GET | `/slow-sqls/top` | `monitor:db:list` | 按指纹聚合 Top N：`hours`（默认 24）、`limit`（默认 10） |
| GET | `/slow-sqls/trend` | `monitor:db:list` | 按小时趋势：条数、平均耗时、最大耗时 |
| GET | `/tables` | `monitor:db:list` | 表容量 Top N（`information_schema.tables`） |
| GET | `/pools` | `monitor:db:list` | 各服务 Hikari 连接池：活跃/空闲/等待/上限，本服务用 MXBean 兜底 |
| DELETE | `/slow-sqls` | `monitor:db:clear` | 清空早于 `beforeDays`（默认 7）的记录，写 `@OperationLog` |

分页统一返回 `Result<PageVO<T>>`（`common` 的 `PageVO`），与后台其他列表一致；前端从 `data.list` / `data.total` 取。

### 7.1 数据库总览的采集口径

`/overview` 里与 MySQL 服务端相关的指标，用 `JdbcTemplate` 查：

```sql
SHOW GLOBAL STATUS WHERE Variable_name IN
  ('Threads_connected','Threads_running','Questions','Slow_queries',
   'Innodb_buffer_pool_reads','Innodb_buffer_pool_read_requests',
   'Innodb_buffer_pool_pages_total','Innodb_buffer_pool_pages_free');
SHOW VARIABLES WHERE Variable_name IN ('max_connections','slow_query_log','long_query_time');
```

- **QPS**：`Questions` 是累计值，需要两次采样差值。复用 `ServerMonitorService` 里 `alertSince` 的思路，把上次采样值 + 时间戳写 Redis（`monitor:db:status:last`，TTL 1 天），本次用 `(ΔQuestions) / Δ秒` 算出；
- **命中率**：`1 - Innodb_buffer_pool_reads / Innodb_buffer_pool_read_requests`；
- **连接数**：`Threads_connected / max_connections`，同时回显 `slow_query_log` 开关（让使用者知道服务端慢日志有没有开）；
- **库表数 / 总容量**：`information_schema.tables` 按 `table_schema = DATABASE()` 聚合，`data_length + index_length`。

### 7.2 连接池指标

两条来源，互补：

1. **各服务**：扩展现有 `ServiceMetricsService`，加采 `hikaricp.connections.active` / `.idle` / `.pending` / `.max`（actuator 已暴露 `metrics`，HikariCP 指标由 Spring Boot 自动绑定，**不需要新依赖**）；
2. **本服务**：直接拿 `HikariDataSource.getHikariPoolMXBean()`，实时且无网络开销，页面标注"当前服务"。

`pending > 0` 是最有价值的告警信号——说明连接池被打满，请求在排队拿连接。

> 落实现前先验证一件事：`GET :9003/actuator/metrics/hikaricp.connections.active` 是否真的返回数据。Spring Boot 4 拆了模块，HikariCP 指标不一定默认绑定；如果 404，就降级为**只展示 system-service 自己的连接池**（`HikariPoolMXBean`，一定有数据），其余服务那一列显示 `-`，不要为了补这一列去改各服务的 metrics 配置。

---

## 8. 前端页面

新增 `src/pages/monitor/database/DatabaseMonitor.vue`，路由挂在 `src/router/index.js` 的 `/monitor` children 下（与 `server` / `cache` 同级），`meta.permission = 'monitor:db:list'`。

布局（自上而下）：

1. **指标卡**（4~6 个渐变卡，沿用库存看板 `Dashboard.vue` 的卡片样式）
   - 连接数 `使用 / 上限`、QPS、今日慢 SQL 条数（超阈值红色）、缓冲池命中率、数据总容量、丢弃数
2. **慢 SQL 趋势**（折线面积图，近 24 小时，双轴：条数 + 平均耗时）
3. **Top 10 慢 SQL**（表格：指纹摘要、服务、次数、平均/最大耗时、最近出现时间；点击行可展开完整 SQL）
4. **连接池状态**（表格：服务、活跃、空闲、等待、上限；`pending > 0` 标红）
5. **慢 SQL 明细分页表**（时间、服务、Mapper 方法、类型、耗时、行数、SQL 摘要；「查看」弹窗展示完整归一化 SQL）
6. **表容量 Top 10**（横向柱状图）

交互要求：

- 时间范围筛选（近 1h / 6h / 24h / 7d）；
- 服务下拉（product / order / auth / user / aftersale / system）；
- 「清空历史」按钮**必须**加 `v-permission="'monitor:db:clear'"`（项目硬规则），点击前二次确认；
- 5 秒自动刷新只刷新指标卡，表格手动刷新，避免打断查看。

`src/api/monitor.js` 追加 6 个方法，命名与现有 `getCacheOverview` / `getMonitorTasks` 保持一致（`getDatabaseOverview` / `getSlowSqls` / `getSlowSqlTop` / `getSlowSqlTrend` / `getTableSizes` / `clearSlowSqls`）。

依赖：ECharts 已在项目中使用（库存看板），无需新增前端依赖。

---

## 9. 权限与菜单 SQL

新增 `docs/sql/20261110_db_monitor.sql`，内容：

```sql
-- 1) 建表（见 §6.1）
-- 2) 菜单：921 数据库监控（parent_id=900 系统监控，sort=5）
-- 3) 按钮：922 清空历史（perms = monitor:db:clear）
-- 4) 超管授权：admin_role_menu 补 921/922
-- 5) 定时任务注册：admin_monitor_task 加 dbSlowSqlCleanup
```

要求（与现有 SQL 脚本一致）：

- 全部 `INSERT ... ON DUPLICATE KEY UPDATE` / `CREATE TABLE IF NOT EXISTS`，**可重复执行**；
- 文件头注明"本地执行后必须同步生产库"；
- 菜单 id 用 **921~922**：908~910 是定时任务按钮、911 是缓存查看 Key、912~920 是财务模块、1000+ 是 AI 模块，都已被占用（`20261105_system_monitor_p2.sql:58-60`、`20261105_monitor_key_masking.sql:7`、`20261105_finance_menu.sql:14-28`）。

---

## 10. 配置项

各服务 `application.yml` 统一加一段（默认值写死在代码里，yml 只是可调）：

```yaml
monitor:
  sql:
    enabled: ${MONITOR_SQL_ENABLED:true}          # 总开关
    slow-threshold-ms: ${MONITOR_SQL_SLOW_MS:300} # 超过多少毫秒才记录
    max-per-minute: ${MONITOR_SQL_MAX_PER_MIN:60} # 每服务每分钟入库上限
    max-sql-length: 2000                          # SQL 文本截断长度
    retain-days: 7                                # 保留天数（system-service 清理用）
```

生产建议：先按 300ms 跑一周，看总量再决定要不要调到 500ms 或 200ms。`deploy/.env.example` 补上三个环境变量并写注释。

---

## 11. 实施步骤

| 阶段 | 内容 | 可独立验证 |
|---|---|---|
| P0 | 建表 + 菜单 SQL，本地执行后同步生产 | 后台能看到「数据库监控」菜单（空页面） |
| P1 | `common` 加 `SqlMonitorInterceptor` + 异步写入 + 配置项；product-service 验证 | 手动执行一条慢查询（`SELECT SLEEP(1)` 或大表全扫），表里出现记录 |
| P2 | `system-service` 加查询接口（明细 / 聚合 / 趋势 / 总览 / 表容量） | Swagger 逐个调通，`@Tag` / `@Operation` 齐全 |
| P3 | 前端页面 + api 封装 + 路由 + 按钮权限 | 页面数据与表内数据一致，非授权账号看不到清空按钮 |
| P4 | 清理任务注册 + `.env.example` 补变量 + 全量验证 | 手动触发清理任务，7 天前数据被删 |

部署顺序：**先发 common 的改动，再逐个重启服务**（product → order → auth → user → aftersale → system）。拦截器对所有服务生效，但没重启的服务不采集，不影响运行。

---

## 12. 验收清单

- [ ] `docs/sql/20261110_db_monitor.sql` 本地执行成功，且**已在生产库执行**
- [ ] 后台菜单出现「系统监控 → 数据库监控」，超管可见，普通角色默认不可见
- [ ] 制造一条 >300ms 的慢 SQL，`admin_db_slow_sql` 有记录，`mapper_method` 能对上具体 Mapper
- [ ] 同一 SQL 不同参数，聚合页归为同一条指纹
- [ ] 页面指标卡数据与 `SHOW GLOBAL STATUS` 手工查询结果一致
- [ ] 连接池 `pending > 0` 时页面标红
- [ ] 「清空历史」按钮：非授权账号不显示；点击后写一条 `admin_operation_log`（`module=system`, `action=DB_SLOW_SQL_CLEAR`）
- [ ] 关闭开关（`MONITOR_SQL_ENABLED=false`）重启后不再采集，业务无异常
- [ ] 数据库连接断开时，页面接口返回明确错误而不是 500 堆栈
- [ ] 所有新接口带 `@Tag` / `@Operation`，Swagger 可见

---

## 13. 风险与边界

| 风险 | 处理 |
|---|---|
| 采集写入加重 DB 负担 | 只记慢 SQL + 每分钟限流 + 有界队列丢弃 + 异步；极端情况可一键关闭 |
| 表无限增长 | 7 天保留 + 每日分批清理；清理本身走索引 `idx_created_at` |
| 敏感数据落库 | 只存归一化 SQL（`?` 占位），**不存参数值**；`mapper_method` 足够定位代码 |
| 慢 SQL 在 DB 故障时集中爆发 | 队列满即丢弃，丢多少在总览「丢弃数」里体现，不重试 |
| 拦截器误伤 file-service / gateway | `@ConditionalOnClass` + common 里 mybatis 依赖 `optional`，无 MyBatis 的服务不装配 |
| user-service 用的是 MyBatis-Plus，拦截器可能不生效 | P1 实测；不生效就接受"该服务暂不采集"，不为了统一去改它的数据访问层 |
| Spring Boot 4 下 hikaricp 指标可能未默认绑定 | P2 先 `curl /actuator/metrics/hikaricp.connections.active` 验证；缺失就降级为只展示 system-service 连接池 |
| 只测到 Java 侧耗时 | 页面注明口径；连接等待看 `hikaricp.connections.pending`，DB 内部执行时间看 MySQL `slow_query_log` |
| MySQL 服务端慢日志未开 | 总览里回显 `slow_query_log` 开关状态，提示运维开启（`SET GLOBAL slow_query_log=ON; long_query_time=1`），本模块不改该配置 |
| 多实例重复清理 | 清理任务只注册在 `system-service`（`task_group='system-service'`），且 `concurrent=1` |
| 生产 SQL 漏同步 | 按项目硬规则：本地执行后必须同步生产，验收清单里单列一项 |
