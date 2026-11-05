# 系统监控模块设计（结合若依与本项目）

> 目标：新增一个**独立的一级菜单「系统监控」**，与「系统管理」平级，不和账号/角色/菜单/日志混在一起。
> 本文只做方案说明，不包含代码实现。

---

## 1. 结论先行

| 事项 | 结论 |
|---|---|
| 菜单位置 | 新建一级目录 `系统监控`（id=900），与 `系统管理`（id=8）平级，排序放在最后 |
| 页面范围 | 服务监控、在线用户、缓存监控、定时任务，共 4 个 |
| 操作日志/登录日志 | **继续留在系统管理**（若依也是放在系统管理的日志管理里），不迁到监控 |
| 后端形态 | 新增 `monitor-service`（端口 9008），从业务服务中独立出来 |
| 数据来源 | OSHI（主机/JVM）+ Docker API（容器）+ Redis INFO/SCAN（缓存）+ Redis 会话（在线用户）+ Spring 调度（任务） |
| 最大改动点 | 当前 JWT 是**无状态**的，token 没有存 Redis，退出登录也不删 key；要做在线用户，必须先补“登录会话登记” |

**边界一句话**：系统管理管“谁能进后台、能点什么”；系统监控看“系统跑得怎么样”。

---

## 2. 若依调研结论（哪些能借鉴、哪些不能照搬）

| 功能 | 若依实现 | 本项目结论 |
|---|---|---|
| 服务监控 | `ServerController` + OSHI `SystemInfo`，采集 CPU/内存/磁盘 + JVM MXBean | **思路照搬**，实现换成 Spring Boot 3 + OSHI 6 |
| 缓存监控 | 基于 Shiro `JCacheManager` 的缓存名/key，不是 Redis 原生命令 | **不照搬**。本项目直接用 Redis，改用 `INFO` + `SCAN` + `MEMORY USAGE` |
| 在线用户 | Shiro `OnlineSessionDAO` + session 持久化，支持单会话踢人 | **不能照搬**。本项目无状态 JWT，需新增 Redis 会话表；一期只能做到账号级强制下线 |
| 定时任务 | 强依赖 Quartz + `sys_job` 表，支持动态 cron、启停、立即执行、日志 | **一期不引入 Quartz**。先做只读任务清单 + 最近执行状态，二期再考虑动态 cron |
| 菜单设计 | 系统监控是独立一级目录，权限 `monitor:server:view`、`monitor:cache:view`、`monitor:job:*` | **照搬结构**，权限码统一用 `monitor:模块:动作` |

---

## 3. 本项目监控范围

### 3.1 四个页面

| 页面 | 核心内容 | 权限码 |
|---|---|---|
| 服务监控 `/monitor/server` | 主机 CPU/内存/磁盘/网络、JVM 堆/GC/线程、各容器状态与资源占用、服务健康检查 | `monitor:server:list` |
| 在线用户 `/monitor/online` | 当前有效登录会话、IP、登录时间、最后活跃、强制下线 | `monitor:online:list` / `monitor:online:logout` |
| 缓存监控 `/monitor/cache` | Redis 运行信息、命中率、内存占用、key 列表（按前缀分组）、TTL、删除 key | `monitor:cache:list` / `monitor:cache:delete` |
| 定时任务 `/monitor/task` | 现有 `@Scheduled` 任务清单、执行周期、最近执行时间/结果、手动执行（白名单） | `monitor:task:list` / `monitor:task:run` |

### 3.2 明确不做（一期）

- 不接 Prometheus / Grafana / SkyWalking；
- 不做 C 端用户监控、接口调用链、异常堆栈表；
- 不做动态 cron（Quartz）、任务日志表；
- 不做缓存“一键清空”（风险太高）；
- 不展示 `/actuator/env`、`/actuator/heapdump` 这类敏感端点。

---

## 4. 技术方案

### 4.1 模块与部署

新增 `monitor-service`：

- 加入父 `pom.xml` 的 `<modules>`；
- 端口 `9008`，注册到 Nacos；
- `deploy/docker-compose.yml` 增加容器，挂载 Docker socket（只读）：

```yaml
monitor-service:
  image: "${REGISTRY:-local}/monitor-service:${TAG:-latest}"
  build: ./monitor-service
  container_name: monitor-service
  restart: always
  depends_on:
    - nacos
  env_file: .env
  volumes:
    - /var/run/docker.sock:/var/run/docker.sock:ro
  networks:
    - my-net
```

网关新增路由（放在 `/api/**` 通配之前）：

```yaml
- id: monitor-service
  uri: lb://monitor-service
  predicates:
    - Path=/api/admin/monitor/**
```

> 不要放在 `/api/admin/system/**` 下，否则会被现有 auth-service 路由整段吃掉。

依赖建议：

```xml
<dependency>
    <groupId>com.github.oshi</groupId>
    <artifactId>oshi-core</artifactId>
    <version>6.6.5</version>
</dependency>
<dependency>
    <groupId>com.github.docker-java</groupId>
    <artifactId>docker-java</artifactId>
    <version>3.3.6</version>
</dependency>
```

并复用 `common` 的 `@RequirePerm`、`PermissionInterceptor`、`IpMasker`、`OperationLog`。

### 4.2 服务监控

**主机/OS（OSHI）**：CPU 核数与使用率（user/system/iowait）、内存与 Swap、磁盘各挂载点、网络收发、运行时长、主机名/OS/架构。

**JVM（MXBean）**：堆已用/最大/已提交、非堆、GC 次数与耗时、线程数/峰值、类加载数、启动时间、运行时长、JDK 版本、启动参数（脱敏）。

**容器（Docker API）**：容器名、状态、CPU%、内存已用/上限/占比、网络 IO、重启次数。这样能直接看到 7 个 Java 服务 + Python `ai-service` + Redis + Nacos 是否正常，比只看宿主机更实用。

**服务健康**：从 Nacos 拿到实例列表后逐个探测；建议给各业务服务加 `spring-boot-starter-actuator`，只暴露 `health`，由 monitor-service 内网调用。网关**不要**转发 `/actuator/**`。

> 注意：OSHI 在容器内读到的 `/proc` 是宿主机视角（内存/CPU 核数通常仍是宿主机），容器实际限额以 Docker stats 为准。页面要注明“主机指标”和“容器指标”分别是什么口径。

### 4.3 在线用户（本项目最大的新增点）

现状（已核对代码）：

- `AuthServiceImpl` 只用了 3 类 Redis key：`auth:captcha:{id}`、`auth:login:fail:{username}:{ip}`、`auth:admin:invalid-before:{adminId}`；
- JWT 有效期 7 天（`jwt.expire=604800`）；
- token 不落 Redis，`/api/logout` 只记登录日志，不做失效；
- 现有“强制失效”是账号级：设置 `invalid-before` 时间戳，让该管理员旧 token 全部失效。

因此在线用户需要补一层会话登记：

| 项目 | 设计 |
|---|---|
| Redis key | `auth:online:{adminId}:{sid}`，Hash |
| 字段 | `username`、`sid`、`ip`、`userAgent`、`loginTime`、`lastActive`、`roles` |
| TTL | 与 JWT 一致（7 天），登录时写入 |
| `sid` | 登录时生成 UUID，写入 JWT claim；老 token 没有 `sid` 也不影响鉴权，只是不进在线列表 |
| 活跃刷新 | 拦截器里节流刷新 `lastActive`：同一会话 10 分钟只写一次（先 `SET NX EX 600` 占位） |
| 退出登录 | 解析 token 取 `sid`，删除对应 key |
| 强制下线 | 一期复用现有机制：`invalid-before = now` 让该账号所有 token 失效，并删除该账号全部 `auth:online:*`；页面文案写“强制下线（该账号全部会话）” |
| 列表 | `SCAN auth:online:*`，按登录时间倒序；IP 展示用 `IpMasker` 脱敏 |

> 如果以后要“只踢某一个设备”，需要给 JWT 加 `jti` 并做 Redis 黑名单，每个请求多一次 Redis 判断。一期不建议做。

### 4.4 缓存监控

**概览（`INFO` 指定 section）**：只取 `server`、`clients`、`memory`、`stats`、`keyspace`，不取 `config`（里面有密码类配置）。

展示：Redis 版本/运行时长、连接数、已用内存/峰值、命中率（`keyspace_hits/(hits+misses)`）、过期/淘汰 key 数、每秒命令数、各 db key 数量。

**缓存列表（`SCAN`）**：

- `SCAN cursor MATCH * COUNT 1000`，单页最多 2000 个 key，避免阻塞；
- 每个 key 展示：key、类型（`TYPE`）、TTL（`TTL`，-1 永久 / -2 不存在）、大小（`MEMORY USAGE`，可选）；
- 按前缀分组：当前主要是 `auth:captcha:*`、`auth:login:fail:*`、`auth:admin:invalid-before:*`，后续业务缓存按 `模块:资源:*` 归组；
- 支持按前缀过滤与关键字搜索。

**删除 key**：

- 必须二次确认 + 记操作日志；
- **禁止删除** `auth:admin:invalid-before:*`（删了会让已强制下线的旧 token 重新可用）；
- 删除 `auth:online:*` 等同踢人，提示文案要写清楚；
- 提供“按前缀批量删除”时，只允许业务缓存前缀白名单，默认不开放“清空全部”。

### 4.5 定时任务

本项目现有 4 个 `@Scheduled` 任务：

| 任务 | 位置 | 周期 |
|---|---|---|
| 日志清理 | `LogCleanupTask#clean` | `0 0 3 * * ?` |
| 超时关闭待付款订单 | `OrderServiceImpl#sweepExpiredPendingPayOrders` | 每 60 秒 |
| 未支付提醒 | `OrderServiceImpl#sweepUnpaidNotify` | 每 60 秒 |
| 自动确认收货 | `OrderServiceImpl#sweepAutoConfirmOrders` | 每 60 秒 |

一期方案：

- 建一个轻量任务注册表（代码内枚举即可），列出任务名、类#方法、周期、说明、是否允许手动执行；
- 用 AOP 注解 `@MonitoredTask("closeExpiredOrders")` 包一层，把最近一次开始/结束/耗时/结果写到 Redis `monitor:task:last:{name}`（过期 7 天），页面只读展示；
- 手动执行只开放白名单（建议先只放 `LogCleanupTask` 这类幂等、低风险任务），每次执行记操作日志。

二期再考虑：`admin_monitor_task` 表 + `ThreadPoolTaskScheduler` 动态注册/启停，不引入 Quartz。

---

## 5. 接口设计

统一前缀 `/api/admin/monitor`，全部要求后台 JWT，权限码见下表。

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/server` | `monitor:server:list` | 主机、JVM、容器、服务健康 |
| GET | `/online` | `monitor:online:list` | 在线会话分页/筛选 username、ip |
| DELETE | `/online/{adminId}` | `monitor:online:logout` | 强制下线该账号全部会话 |
| GET | `/cache/overview` | `monitor:cache:list` | Redis INFO 概览 |
| GET | `/cache/keys` | `monitor:cache:list` | 参数 `prefix`、`cursor`、`pageSize` |
| DELETE | `/cache/key` | `monitor:cache:delete` | 参数 `key`，危险 key 后端直接拒绝 |
| GET | `/tasks` | `monitor:task:list` | 任务清单 + 最近执行 |
| POST | `/tasks/{name}/run` | `monitor:task:run` | 白名单任务手动执行（二期可先不做） |

返回统一用项目现有 `Result` 结构；IP 字段返回前用 `IpMasker` 脱敏。

---

## 6. 菜单与权限 SQL

新增一级目录，ID 段用 900+，不影响现有 1~8 和 800~840。

```sql
-- 一级目录：系统监控
INSERT INTO admin_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status, create_time, update_time)
VALUES (900, 0, '系统监控', 'M', NULL, NULL, NULL, 'MonitorOutlined', 10, 1, 1, NOW(), NOW());

-- 页面
INSERT INTO admin_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status, create_time, update_time)
VALUES
(901, 900, '服务监控', 'C', '/monitor/server', NULL, 'monitor:server:list', NULL, 1, 1, 1, NOW(), NOW()),
(902, 900, '在线用户', 'C', '/monitor/online', NULL, 'monitor:online:list', NULL, 2, 1, 1, NOW(), NOW()),
(903, 900, '缓存监控', 'C', '/monitor/cache', NULL, 'monitor:cache:list', NULL, 3, 1, 1, NOW(), NOW()),
(904, 900, '定时任务', 'C', '/monitor/task', NULL, 'monitor:task:list', NULL, 4, 1, 1, NOW(), NOW());

-- 写操作按钮
INSERT INTO admin_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status, create_time, update_time)
VALUES
(905, 902, '强制下线', 'F', NULL, NULL, 'monitor:online:logout', NULL, 1, 1, 1, NOW(), NOW()),
(906, 903, '删除缓存', 'F', NULL, NULL, 'monitor:cache:delete', NULL, 1, 1, 1, NOW(), NOW()),
(907, 904, '执行任务', 'F', NULL, NULL, 'monitor:task:run', NULL, 1, 1, 1, NOW(), NOW());
```

规则：

- 超管 `*:*:*` 自动可见，无需授权；
- 其他角色在「角色管理 → 分配菜单」里勾选；
- **本地库执行后必须同步生产库**（按项目约定）；
- 按钮按现有规则：菜单无权限隐藏，写操作按钮可见、点击提示“无权限”，不置灰。

---

## 7. 前端改造

`src/router/index.js` 在 `/system` 之后新增 `/monitor` 一级目录（`renderMenu: true`），子路由与 `admin_menu.path` 保持一致：

| 路由 | 页面 | meta.permission |
|---|---|---|
| `/monitor/server` | `pages/monitor/server/ServerMonitor.vue` | `monitor:server:list` |
| `/monitor/online` | `pages/monitor/online/OnlineUserList.vue` | `monitor:online:list` |
| `/monitor/cache` | `pages/monitor/cache/CacheMonitor.vue` | `monitor:cache:list` |
| `/monitor/task` | `pages/monitor/task/TaskList.vue` | `monitor:task:list` |

接口统一放 `src/api/monitor.js`。页面要点：

- 服务监控：卡片 + 进度条展示 CPU/内存/磁盘，容器表格标红异常状态；5 秒轮询，页面隐藏时暂停；
- 在线用户：IP 列用现有 `mask.js` 脱敏；强制下线二次确认；
- 缓存监控：概览卡片 + key 表格 + 前缀筛选；删除按钮用 `v-permission="'monitor:cache:delete'"`；
- 定时任务：最近执行结果用状态标签，手动执行只对白名单任务显示按钮。

侧边栏是静态路由驱动 + 菜单表做权限，两边都要加，缺一不可。

---

## 8. 分期实施

### P0（先把架子搭起来）

1. 新增 `monitor-service`、父 pom、compose、Nacos、网关路由；
2. 前端新增 `/monitor` 一级菜单与 4 个空页面 + 路由；
3. 菜单 SQL 本地执行并同步生产；
4. 服务监控（OSHI + JVM + Docker stats + 健康检查）；
5. 缓存监控只读（INFO + SCAN）。

### P1（补交互）

1. 登录会话登记（`sid` + `auth:online:*`）；
2. 在线用户列表 + 强制下线；
3. 缓存删除（白名单/黑名单约束）；
4. 定时任务清单 + 最近执行记录。

### P2（可选增强）

1. 各业务服务 Actuator/Micrometer 指标聚合（每服务 JVM、接口耗时）；
2. 动态 cron（`admin_monitor_task` 表 + `ThreadPoolTaskScheduler`）；
3. 单会话踢人（JWT `jti` 黑名单）；
4. 监控数据保留与告警（CPU/内存/磁盘阈值）。

### 验收标准

- 「系统监控」在侧边栏独立显示，不与系统管理混排；
- 无权限角色看不到监控菜单；写按钮可见但点击提示无权限；
- 服务监控能正确显示 8 个 Java 服务 + ai-service + Redis + Nacos 状态；
- 在线用户与 Redis 实际会话一致，强制下线后旧 token 立即 401；
- 缓存列表不阻塞 Redis（SCAN 游标分页），删除有日志；
- 定时任务清单与实际 `@Scheduled` 一致；
- 本地与生产菜单/权限数据一致。

---

## 9. 风险与注意事项

1. **Docker socket 等于宿主机 root 权限**：只读挂载也仍可读容器信息；monitor-service 绝不能对公网开放，网关只允许 `/api/admin/monitor/**`，且必须后台 JWT。
2. **缓存删除很危险**：删除 `auth:admin:invalid-before:*` 会让已下线的 token 复活；删除 `auth:online:*` 等于踢人。后端必须做 key 前缀黑名单，不能只靠前端确认。
3. **在线用户会新增写操作**：登录写、退出删、活跃节流写；要保证 Redis 不可用时不影响登录主流程（失败只记日志）。
4. **OSHI 口径**：容器内看到的主机指标可能不等于容器限额，页面要区分“主机”和“容器”，避免误判。
5. **Actuator 暴露**：只给内网调用，网关不转发 `/actuator/**`，不要开 `env`、`heapdump`、`threaddump`。
6. **菜单同步**：按项目约定，本地执行 SQL 后必须同步生产，否则线上会缺菜单/权限。
7. **强制下线粒度**：一期是账号级，页面文案必须写清楚；需要单设备踢人再上 `jti` 方案。

---

## 10. 数据库建表说明

### 10.1 一期（P0/P1）：0 张新表

监控数据都是“运行时状态”，不需要落库：

| 数据 | 存储方式 | 不建表的原因 |
|---|---|---|
| 服务监控（CPU/内存/容器/JVM） | 实时采集，直接返回 | 只是当下快照，没有历史趋势需求 |
| 在线用户 | Redis `auth:online:{adminId}:{sid}`，TTL 7 天 | 会话是临时数据，Redis TTL 天然过期；落库还要自己清理 |
| 缓存监控/缓存列表 | Redis `INFO` / `SCAN` | 数据本来就在 Redis 里，再存一份没有意义 |
| 定时任务最近执行状态 | Redis `monitor:task:last:{name}`，TTL 7 天 | 页面只看“最近一次”，不需要完整历史 |
| 监控操作（删缓存/踢人） | 复用现有 `admin_operation_log` | 已有审计链路，不另建表 |

> 结论：菜单 SQL 之外，**一期不需要执行任何建表语句**。这是有意的——能放 Redis 的临时状态就不要进 MySQL。

### 10.2 二期（P2，做动态定时任务时才建）：2 张表

只有需要“后台新增任务、改 cron、启停、看执行历史”时，才需要这两张表。

> 表名统一加 `admin_` 前缀（后台管理系统专用，与 `admin_operation_log` 一致），避免库内表多时找不到。

#### admin_monitor_task（任务配置）

```sql
CREATE TABLE admin_monitor_task (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_name       VARCHAR(100)    NOT NULL COMMENT '任务名称，页面展示用，唯一',
    task_group      VARCHAR(64)     NOT NULL DEFAULT 'default' COMMENT '分组',
    bean_name       VARCHAR(200)    NOT NULL COMMENT 'Spring Bean 名称',
    method_name     VARCHAR(100)    NOT NULL COMMENT '执行方法名',
    method_params   VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '方法参数字符串',
    task_type       TINYINT         NOT NULL DEFAULT 1 COMMENT '1 cron 2 fixedDelay 3 fixedRate',
    schedule_value  VARCHAR(100)    NOT NULL COMMENT 'cron 表达式，或 fixedDelay/fixedRate 的毫秒数',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '0 暂停 1 运行',
    concurrent      TINYINT         NOT NULL DEFAULT 0 COMMENT '0 允许并发 1 禁止并发',
    remark          VARCHAR(255)             DEFAULT NULL COMMENT '备注',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_name (task_name),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务配置';
```

说明：

- `bean_name` + `method_name` 用反射调用，所以只允许注册受信任的 Bean，不能从前端任意填类名；
- `task_type` 把 cron 和固定间隔统一到一张表；
- `status` 控制启停，运行中的任务在应用启动时由 `ThreadPoolTaskScheduler` 重新注册；
- 不建议加外键，与现有表保持一致。

#### admin_monitor_task_log（执行记录）

```sql
CREATE TABLE admin_monitor_task_log (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id         BIGINT UNSIGNED NOT NULL COMMENT '任务ID',
    task_name       VARCHAR(100)    NOT NULL COMMENT '任务名称冗余，任务删除后日志仍可读',
    trigger_type    TINYINT         NOT NULL DEFAULT 1 COMMENT '1 定时触发 2 手动触发',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '1 成功 0 失败',
    error_msg       VARCHAR(1024)            DEFAULT NULL COMMENT '失败原因',
    cost_ms         INT             NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
    started_at      DATETIME        NOT NULL COMMENT '开始时间',
    finished_at     DATETIME                 DEFAULT NULL COMMENT '结束时间',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_task_started (task_id, started_at),
    KEY idx_started_at (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务执行记录';
```

说明：

- 只保留最近 30 天，由现有 `LogCleanupTask` 扩展一段清理逻辑（或加一个独立的清理任务），避免表无限增长；
- `task_name` 冗余一份，任务被删除后历史日志还能看懂；
- 失败记录必须落库，成功记录可以按需落（全部落的话 4 个每分钟任务约 17 万行/月，能接受但要配清理）。

### 10.3 不建议现在建的表

| 表 | 为什么不建 |
|---|---|
| `admin_monitor_metric_snapshot`（CPU/内存历史） | 需要定时采样 + 聚合 + 清理，数据量大；一期用实时快照足够。以后要看趋势，优先上 Prometheus，而不是自己建表存指标 |
| `admin_monitor_alert_rule`（告警规则） | 一期没有告警渠道（短信/企微/邮件），建表也用不上 |
| `admin_login_session`（在线用户落库） | Redis TTL 已经够用；落库要处理过期、重启恢复、多实例一致性，得不偿失 |
| `admin_monitor_cache_key`（缓存清单） | Redis 自己就是数据源，存一份只会不同步 |

### 10.4 涉及的 Redis key（不是表）

| key | 类型 | TTL | 用途 |
|---|---|---|---|
| `auth:online:{adminId}:{sid}` | Hash | 7 天 | 在线会话：username/ip/userAgent/loginTime/lastActive |
| `auth:online:touch:{sid}` | String | 10 分钟 | 活跃时间刷新节流，避免每个请求都写 Redis |
| `monitor:task:last:{taskName}` | Hash | 7 天 | 最近一次执行：开始/结束/耗时/结果 |

