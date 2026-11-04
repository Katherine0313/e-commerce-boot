# RBAC 操作日志 / 登录日志 实施方案

> 生成日期：2026-11-04（v3，按本项目实际代码与库表定制）
> 参考了 `docs/芋道（yudao）日志体系完整梳理.docx`，只借鉴它的**做法**（DIFF、脱敏、导出、保留策略），不引入它的框架和表名。

---

## 零、本方案到底写哪些日志（先看这里）

**只做两大类，都是「后台管理员」的审计日志；不做 C 端、不做接口访问日志。**

### 第 1 类：操作日志（表 `operation_log`）

记录管理员「改了什么东西」，写操作才记，查询不记。

| 范围 | 具体动作 | 现状 |
|---|---|---|
| 售后 | 审核通过/拒绝、删除售后单 | 已打点 2 处 |
| 发货 | 发货、补发、修改物流、快递公司新增/编辑/删除 | 已打点 6 处 |
| RBAC | 管理员账号增/改/删/重置密码；角色增/改/删/分配菜单；菜单增/改/删 | **待补** |
| 商品 | 编辑、上下架、改价、库存调整 | **待补** |
| 订单 | 取消、关闭、修改备注 | **待补** |
| 营销 | Banner、优惠券、文章的新增/编辑/删除 | **待补** |

### 第 2 类：登录日志（表 `admin_login_log`）

记录管理员「登录/登出」本身，不记录登录后的业务操作。

| 记录内容 | 取值 |
|---|---|
| 事件 | `LOGIN` / `LOGOUT` |
| 结果 | `SUCCESS` / `BAD_CREDENTIALS` / `USER_DISABLED` / `CAPTCHA_ERROR` / `LOCKED` / `PARAM_MISSING` |
| 附带信息 | 账号、IP、User-Agent、失败原因文本、时间 |

### 明确不做的

| 不做的日志 | 原因 |
|---|---|
| API 访问日志（每次请求入参出参耗时） | 全量落库会把表撑大，现有网关日志 + `docs/troubleshooting.md` 够用 |
| API 错误日志（异常堆栈表） | 本期不做，列为下一期可选项 |
| C 端用户操作日志 | 按你的决定，只审计后台登录用户 |
| 控制台请求打印 | 生产环境日志量太大 |

> 一句话：**后台管理员「做了什么业务变更」→ 操作日志；「怎么登录进来的」→ 登录日志。就这两类。**

---

## 一、结论先行

操作日志的基建已经存在：`operation_log` 表有 14 个字段（本地已累计约 60 条），`@OperationLog` + `OperationLogAspect` + `JdbcOperationLogRecorder` 都在工作，售后审核、售后删除、发货、补发、改物流、快递公司增删改共 8 处已打点。

真正缺的是「能查、能看到改了什么、能交付审计报表」：

1. 后台菜单里已经有「操作日志」这一项（`admin_menu` id=803，路径 `/system/logs`，权限码 `system:log:list`），但**没有后端接口、没有前端页面**，点进去是空的 —— 这是个已经占位、尚未实现的菜单。
2. 没有登录日志表，也没有任何登录/登出记录，账号爆破、误锁定、账号停用都查不到。
3. `operation_log` 只存 `user_id`，没有 `username` 快照，账号删除或改名后无法识别操作人。
4. `params` 原样落库，重置密码、手机号等敏感参数有泄露风险。
5. 采集器 `OperationLogCollector.clientIp()` 只认 `X-Forwarded-For`，与登录接口已经改用的 `EO-Connecting-IP` 口径不一致。
6. 建表 SQL 不在仓库里（`docs/sql/` 下没有 `operation_log` 的 DDL），新环境会漏建表，写入失败只打 warn。
7. 8 处打点都只写了动作，没有「改前→改后」；并且 RBAC、商品改价、订单状态变更完全没有打点。
8. 没有保留策略，`operation_log` 会一直增长。

实施顺序：**P0 补字段/脱敏 → P0 登录日志 → P0 查询接口与两个页面 → P1 打点覆盖 + 变更内容 → P2 导出/清理**。

---

## 二、与本项目对齐的现状

### 2.1 `operation_log` 实际结构（本地与生产一致）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint | 自增主键 |
| `user_id` / `user_type` | bigint / varchar(16) | 操作人 ID 与类型（admin / user） |
| `module` / `action` | varchar(64) | 模块与动作，现有值如 `aftersales/REVIEW`、`delivery/SHIP` |
| `biz_id` | bigint | 业务对象 ID |
| `detail` | varchar(255) | 补充说明，支持 SpEL；**目前 8 处打点都没有用到** |
| `method` / `uri` / `ip` / `params` | — | 请求方法、路径、IP、参数 JSON（2000 字符截断） |
| `success` / `error_msg` / `cost_ms` | — | 结果、失败原因、耗时 |
| `created_at` | datetime | 由 Java 传入 |

索引：`idx_operation_log_module`、`idx_operation_log_user`、`idx_operation_log_created`。

### 2.2 现有打点（8 处）

| 文件 | 方法 | module / action |
|---|---|---|
| `aftersale-service/.../AdminAfterSaleServiceImpl.java:309` | `review` | aftersales / REVIEW |
| `AdminAfterSaleServiceImpl.java:349` | `delete` | aftersales / DELETE |
| `order-service/.../OrderDeliveryServiceImpl.java:63` | `ship` | delivery / SHIP |
| `OrderDeliveryServiceImpl.java:103` | `resend` | delivery / RESEND |
| `OrderDeliveryServiceImpl.java:125` | `updateDelivery` | delivery / UPDATE_DELIVERY |
| `order-service/.../DeliveryExpressServiceImpl.java:42` | `save` | delivery / EXPRESS_SAVE |
| `DeliveryExpressServiceImpl.java:71` | `update` | delivery / EXPRESS_SAVE |
| `DeliveryExpressServiceImpl.java:95` | `remove` | delivery / EXPRESS_REMOVE |

### 2.3 现有菜单（`admin_menu` 实际数据）

| id | 名称 | 路径 | 权限码 |
|---|---|---|---|
| 8 | 系统管理 | — | — |
| 803 | 操作日志 | `/system/logs` | `system:log:list`（**有菜单无实现**） |
| 800 / 801 / 802 | 管理员账号 / 角色管理 / 菜单管理 | `/system/users` 等 | `system:user:list` 等 |

权限码约定是「小写模块:对象:动作」（`system:user:add`），后端 `action` 约定是全大写（`REVIEW`、`SHIP`）。方案沿用这两套约定，不改成芋道的 `bizType/subType` 命名。

### 2.4 关键代码事实

- `UserContext` 只有 `userId/userType/roles/permissions`，没有 username；但管理员 JWT 已带 username claim（`JwtUtil.generateAdminToken(id, username, roles, perms)`）。
- `AuthService.login()`（`auth-service/.../AuthServiceImpl.java:81-116`）有 5 个失败分支：参数为空、已锁定、验证码错误、账密错误、账号停用；成功在 `buildLoginResult()`。
- `AuthController.logout()` 是无状态 JWT，直接返回成功，没有记录。
- `AuthServiceApplication` 只有 `@MapperScan("ecommerce.auth.mapper")`，没有 `@EnableScheduling`，P2 定时清理需要补。
- 前端已有 `v-permission` 指令（`src/directives/permission.js`，在 `main.js` 注册）、`src/utils/mask.js` 手机号脱敏工具、`src/api/system.js` 系统管理接口文件。
- 父 pom 已统一管理 `poi-ooxml` 6.0.0，`order-service` 已在使用；auth-service 直接引依赖即可，不新增版本。

---

## 三、为什么不照搬芋道

| 芋道做法 | 本项目怎么处理 | 原因 |
|---|---|---|
| `mzt-biz-log` + `@LogRecord` + `@DiffLogField` + `#_DIFF` | **不引依赖**，沿用现有 `@OperationLog`，另加一个 `LogDetail` ThreadLocal 和 `LogDiff` 小工具 | 换框架要重写 8 处打点并加第三方 starter；我们只缺「新旧值对比」这一小块 |
| Security 登录流程自动记录登录日志 | 在 `AuthServiceImpl.login()` / `AuthController.logout()` 里显式调用记录器 | 本项目是自研 JWT，没有 Spring Security 登录链路 |
| `LoginLogTypeEnum` 6 种登录方式、`system_login_log` | `admin_login_log`，`login_type` 只保留 `LOGIN` / `LOGOUT` | 目前只有账号密码登录，没有社交/短信/Mock 登录，不预留不存在的枚举 |
| `infra_api_access_log` 全量访问日志 | **不做** | 每次请求都落库会把表撑大；网关日志 + `docs/troubleshooting.md` 的排查流程已够用 |
| `infra_api_error_log` 异常日志 | **本期不做**，列为下一期可选项 | 各服务 `docker logs` + 全局异常处理已能定位；要做也应单独建表，不混进操作日志 |
| `bizType / subType / bizNo` 三段式 | 继续用现有 `module / action / biz_id` | 表结构和 8 处打点已经按这套设计，改字段会牵连数据迁移 |
| `system_operate_log` 表名 | 继续用 `operation_log` | 生产已有数据 |

---

## 四、数据模型改造

### 4.1 `operation_log`（增量，不动存量）

```sql
-- 1) 操作人快照
ALTER TABLE operation_log
  ADD COLUMN username VARCHAR(64) NULL COMMENT '操作人账号快照' AFTER user_id;

-- 2) DIFF 文案比 255 长，扩到 1024
ALTER TABLE operation_log
  MODIFY COLUMN detail VARCHAR(1024) NULL COMMENT '补充说明/变更详情';

-- 3) 复合索引（现有单列索引保留）
ALTER TABLE operation_log
  ADD INDEX idx_operation_log_module_action (module, action, created_at),
  ADD INDEX idx_operation_log_username (username, created_at);
```

### 4.2 新建 `admin_login_log`（与现有 `admin_*` 表命名一致）

```sql
CREATE TABLE admin_login_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  admin_id    BIGINT       NULL COMMENT '管理员 ID，账号不存在时为空',
  username    VARCHAR(64)  NOT NULL COMMENT '登录名（原样保留，便于排查爆破）',
  login_type  VARCHAR(16)  NOT NULL COMMENT 'LOGIN / LOGOUT',
  result_code VARCHAR(24)  NOT NULL COMMENT 'SUCCESS / BAD_CREDENTIALS / USER_DISABLED / CAPTCHA_ERROR / LOCKED / PARAM_MISSING',
  reason      VARCHAR(128) NULL COMMENT '失败原因文本',
  ip          VARCHAR(64)  NULL COMMENT '客户端 IP',
  user_agent  VARCHAR(255) NULL COMMENT 'User-Agent',
  created_at  DATETIME     NOT NULL COMMENT '发生时间',
  PRIMARY KEY (id),
  KEY idx_login_log_username (username, created_at),
  KEY idx_login_log_created (created_at),
  KEY idx_login_log_ip (ip, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='后台登录/登出日志';
```

### 4.3 建表脚本入库

新增 `docs/sql/log/01-operation-log-schema.sql`（按 2.1 的真实 DDL 回写）、`02-operation-log-add-username.sql`、`03-admin-login-log.sql`、`04-log-menu-seed.sql`。以后新环境按编号顺序执行。

---

## 五、后端改造

### 5.1 common 公共层

| 改动 | 文件 | 内容 |
|---|---|---|
| username 上下文 | `common/.../utils/UserContext.java` | 新增 `CURRENT_USERNAME` ThreadLocal、`setUsername/getUsername`，`remove()` 清理 |
| 解析 claim | `common/.../interceptor/JwtInterceptor.java` | 读取 `claims.get("username")` 写入 `UserContext` |
| 采集器 | `common/.../log/OperationLogCollector.java` | `currentUsername()`；`clientIp()` 改为 `EO-Connecting-IP` → `X-Forwarded-For` 首段 → `X-Real-IP` → `remoteAddr` |
| 参数脱敏 | 同上 `paramsJson()` | `password/pwd/token/secret` 值 → `***`；11 位手机号 → `138****5678`；仍截断 2000 字符 |
| 条目/写入 | `OperationLogEntry.java`、`JdbcOperationLogRecorder.java` | 增加 `username` 字段与 INSERT 列 |
| 变更内容 | 新增 `common/.../log/LogDetail.java`、`LogDiff.java` | 见 5.2 |

### 5.2 变更内容（替代芋道的 `#_DIFF`，不引入注解扫描）

`LogDetail`：ThreadLocal 累加器，Service 里把变更文案放进去；`OperationLogAspect` 在 `detail` SpEL 为空时回退取 `LogDetail.join()`，并在 `finally` 清理。

`LogDiff`：只提供两个方法，不做反射扫描：

```java
LogDiff.of("销售价", 199, 179);                       // 【销售价】从【199】修改为【179】
LogDiff.of("售后状态", 1, 2, AfterSaleStatusText::of); // 用解析函数翻译成中文
```

用法（以现有打点为例）：

```java
// AdminAfterSaleServiceImpl#review（原第 309 行注解保持不变）
Integer oldStatus = afterSale.getStatus();
// ... 审核逻辑
LogDetail.add(LogDiff.of("售后状态", oldStatus, newStatus, this::statusText));
```

约束：

- 只对能拿到旧值的方法使用；拿不到旧值就写纯文本，不硬凑 DIFF。
- 全大写 `action`、`module` 沿用现有风格，`LogDetail` 不改变注解签名。
- 密码、手机号、身份证字段不进入 DIFF 文案。

### 5.3 登录日志（auth-service）

新增：

- `auth-service/.../entity/AdminLoginLog.java`
- `auth-service/.../mapper/AdminLoginLogMapper.java` + `resources/mappers/auth/AdminLoginLogMapper.xml`
- `auth-service/.../service/LoginLogService.java`（内部 try/catch，失败只 warn）
- `auth-service/.../enums/LoginType.java`、`LoginResult.java`（值见 4.2 注释，不做 6 种登录方式）

打点位置（对齐 `AuthServiceImpl` 的真实分支）：

| 位置 | login_type | result_code | reason |
|---|---|---|---|
| `login()` 参数为空 | LOGIN | PARAM_MISSING | 用户名和密码不能为空 |
| `assertNotLocked()` 抛锁 | LOGIN | LOCKED | 登录失败次数过多，已锁定 |
| `validateCaptcha()` 抛错 | LOGIN | CAPTCHA_ERROR | 验证码错误或已过期 |
| `adminMapper.findByUsername()` 为空 / 密码不匹配 | LOGIN | BAD_CREDENTIALS | 用户名或密码错误 |
| 账号停用 | LOGIN | USER_DISABLED | 账号已停用 |
| `buildLoginResult()` 成功 | LOGIN | SUCCESS | — |
| `AuthController.logout()` | LOGOUT | SUCCESS | — |

实现方式：显式调用 `LoginLogService.record(...)`，同步 insert（登录量小），不用 AOP；失败原因分散在多个分支，AOP 拿不到语义。

### 5.4 查询接口（auth-service，复用 803 菜单）

| 接口 | 权限码 | 说明 |
|---|---|---|
| `GET /api/admin/system/logs` | `system:log:list`（**复用 803 已有权限码**） | 操作日志分页 |
| `GET /api/admin/system/login-logs` | `system:log:login:list` | 登录日志分页 |
| `GET /api/admin/system/logs/export?format=csv\|xlsx` | `system:log:export` | 操作日志导出（≤5 万行） |
| `GET /api/admin/system/login-logs/export?format=csv\|xlsx` | `system:log:login:export` | 登录日志导出（≤5 万行） |

新增 `AdminOperationLogMapper` + XML（读 `operation_log`，与日志写入方共用同一个库，不新建服务）。

操作日志查询参数：`page/pageSize/module/action/username/success/startTime/endTime/keyword`；默认近 7 天，`pageSize ≤ 100`，SQL 用 `LIMIT #{offset}, #{pageSize}`（生产 MySQL 5.7）。返回 `id, username, userType, module, action, bizId, detail, method, uri, ip, params, success, errorMsg, costMs, createdAt`。

登录日志查询参数：`page/pageSize/username/loginType/resultCode/ip/startTime/endTime`；返回 `id, username, adminId, loginType, resultCode, success, reason, ip, userAgent, createdAt`。

### 5.5 菜单与权限种子（基于现有 803，不重复插入）

```sql
-- 804 登录日志（803 操作日志已存在，直接复用）
INSERT INTO admin_menu (id, parent_id, menu_name, menu_type, path, perms, icon, sort_order, status)
VALUES (804, 8, '登录日志', 'C', '/system/logs/login', 'system:log:login:list', 'icon-login', 5, 1);

-- 两个导出按钮（839/840 当前未被占用）
INSERT INTO admin_menu (id, parent_id, menu_name, menu_type, perms, sort_order, status) VALUES
(839, 803, '日志导出', 'F', 'system:log:export', 1, 1),
(840, 804, '日志导出', 'F', 'system:log:login:export', 1, 1);

-- 超管授权（role_key='admin'）
INSERT INTO admin_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM admin_role r
JOIN admin_menu m ON m.id IN (804, 839, 840)
WHERE r.role_key = 'admin';
```

### 5.6 P1 打点覆盖（按本项目现有 Controller/Service）

| 模块 | 目标类/方法 | module / action | 变更内容 |
|---|---|---|---|
| 管理员账号 | `AdminSystemUserServiceImpl` 增/改/删/重置密码 | `system` / `USER_ADD` `USER_EDIT` `USER_REMOVE` `RESET_PWD` | 昵称、手机号（脱敏）；重置密码 `recordParams = false` |
| 角色 | `AdminSystemRoleServiceImpl` 增/改/删/分配菜单 | `system` / `ROLE_ADD` `ROLE_EDIT` `ROLE_REMOVE` `ROLE_ASSIGN` | 角色名、菜单数量 |
| 菜单 | `AdminSystemMenuServiceImpl` 增/改/删 | `system` / `MENU_ADD` `MENU_EDIT` `MENU_REMOVE` | 菜单名、路径、权限码 |
| 商品 | `AdminProductServiceImpl` 编辑/上下架/改价/库存 | `product` / `PRODUCT_EDIT` `SHELF` `PRICE_CHANGE` `STOCK_ADJUST` | 改价「销售价 199→179」 |
| 订单 | `AdminOrderServiceImpl` 取消/关闭/备注 | `order` / `CANCEL` `CLOSE` `REMARK` | 状态、备注 |
| 营销 | Banner/优惠券/文章 Service 的增改删 | `promotion` / `BANNER_*` `COUPON_*` `ARTICLE_*` | 名称、状态、数量 |

原则：只打「改变业务状态或权限」的写操作；查询、列表、导出以外的读接口不打点。商品 `specs` JSON 变更只记「规格项数量/名称」，不落整段 JSON。

### 5.7 P2 导出与清理

- CSV：`HttpServletResponse` 直接写流，`text/csv;charset=UTF-8` + `Content-Disposition`。
- Excel：`SXSSFWorkbook`（流式，窗口 100 行），auth-service `pom.xml` 引 `poi-ooxml`（版本由父 pom 管理）；超过 5 万行返回 400，提示缩小时间范围。
- 导出动作本身记一条 `system / LOG_EXPORT`。
- 清理：`AuthServiceApplication` 补 `@EnableScheduling`，新增每日 03:00 任务，每批 1000 行删除 `created_at < NOW() - INTERVAL 180 DAY`，并记 `system / LOG_CLEAN`。

---

## 六、前端改造（e-commerce 后台）

| 改动 | 文件 | 内容 |
|---|---|---|
| API | `src/api/system.js` | `getOperationLogs`、`getLoginLogs`、两个 blob 导出方法 |
| 操作日志页 | 新建 `src/pages/system/logs/OperationLogList.vue` | 筛选（模块/动作/账号/结果/时间）、表格、详情抽屉（`params` 格式化 JSON、`detail` 原样显示） |
| 登录日志页 | 新建 `src/pages/system/logs/LoginLogList.vue` | 筛选（账号/类型/结果码/IP/时间）、失败结果标红 |
| 路由 | `src/router/index.js`（系统管理分组） | `/system/logs`（操作日志，权限 `system:log:list`）、`/system/logs/login`（`system:log:login:list`） |
| 导出 | 两个页面 | `v-permission="'system:log:export'"` / `'system:log:login:export'`；`responseType: 'blob'`，文件名带时间范围 |
| 脱敏 | 复用 `src/utils/mask.js` | 列表里的手机号（如 `params` 明文意外存在时）兜底展示为 `138****5678` |

交互：默认近 7 天；`module/action` 下拉来自常量，不手输；详情抽屉空值显示「—」；不提供删除按钮。

---

## 七、实施顺序与验收

| 阶段 | 内容 | 验收标准 |
|---|---|---|
| P0-1 数据 | 4.1、4.2、5.5，DDL 入库 `docs/sql/log/` | 本地/生产 `SHOW COLUMNS` 一致；804/839/840 菜单出现 |
| P0-2 common | UserContext / JwtInterceptor / Collector / Entry / Recorder | 新日志有 username；重置密码查库无明文；手机号 `138****5678` |
| P0-3 登录日志 | LoginLogService + 5 个失败分支 + logout | 正确密码 SUCCESS；错误密码 BAD_CREDENTIALS；锁定 LOCKED 各 1 条 |
| P0-4 查询接口 | 4 个接口 + 权限码 | 超管 200；operator 403；无 token 401 |
| P0-5 前端 | 两个页面 + 路由（复用 803 菜单） | 点「操作日志」能看到数据；夜间模式样式正常 |
| P1-1 变更内容 | `LogDetail` + `LogDiff` + 2~3 处改造 | 售后审核 detail 显示「售后状态 待审核→审核通过」 |
| P1-2 打点 | 5.6 清单 | 每个动作操作后能查到记录且带变更内容 |
| P2 导出/清理 | CSV + Excel + 定时清理 | 两种格式行数一致；超 5 万行返回 400；清理 dry-run 正确 |

发布顺序：DDL → `auth-service`（接口/登录日志/导出）→ common + 业务服务（P1 打点）→ 后台前端。
回滚：前端移除路由；日志写入全部 try-catch，不阻塞业务；新表可 `RENAME` 留档。

---

## 八、风险与注意事项

1. **异步写入可能丢日志**：线程池满或停机时队列里的条目会丢；当前定位是「丢了不影响业务」。若审计要求强一致，再改同步写。
2. **`LogDetail` 是 ThreadLocal**：必须在业务方法内 `add`，切面 `finally` 清理；跨线程（异步任务）里不会继承，不要在里面用。
3. **敏感字段**：`params` 只做字符串级脱敏，嵌套结构可能漏；重置密码等接口直接 `recordParams = false`。
4. **大表查询**：默认 7 天 + `pageSize ≤ 100`，否则 MySQL 5.7 会扫全表。
5. **导出内存**：Excel 必须 `SXSSFWorkbook`，CSV 直接写流；禁止 5 万行先入 List。
6. **file-service 无 JdbcTemplate**：文件上传不会产生操作日志；若需要，单独接数据源。
7. **多服务共用 `e_commerce_db`**：日志读写都依赖这一点；将来拆库要改成独立日志服务。
8. **时间口径**：日志时间由 Java 生成，查询接口统一用应用时间，不与数据库 `NOW()` 混用。

---

## 九、已确认的决策

| 问题 | 决策 |
|---|---|
| 保留期限 | 180 天，定时清理 |
| 页面可见范围 | 仅「店铺管理员（超管）」角色 |
| 导出格式 | CSV + Excel 都要，独立导出权限码 |
| C 端操作 | 不记录，只记录后台登录用户 |
| `params` 手机号 | 脱敏为 `138****5678` |

---

## 十、仍建议确认的点

1. API 错误日志（异常接口 + stackTrace）本期不做，是否排到下一期？
2. 日志页是否支持按 `bizId` 跳到售后单/订单详情？要做的话需要各模块提供详情路由。
3. 导出是否需要包含已删除账号的历史记录？当前用 `username` 快照，可以保留。

---

## 十一、附录：三份文档中的其他未完成项（与本次方案无关，供排期参考）

> 状态可能已被后续发布覆盖，落地前逐条复核。

**安全 / 审计**
- 网关限流、分接口差异化限流、IP 黑名单（`task_plan.md` 164-168 行）
- 改价/退款/导出等敏感操作二次授权（`task_plan.md` 294 行）
- 登录 MFA、IP 白名单、登录设备管理（`task_plan.md` 295 行）
- 删除生产演示账号、去掉登录页演示预填（`task_plan.md` 283 行）

**功能**
- 动态路由（暂缓，`task_plan.md` 282 行）
- PC 商城详情页 specs、首页分类/轮播占位（`task_plan.md` 92 行）
- 购物车加购校验上架状态、购物车 5 接口端到端验证（`task_plan.md` 109-110 行）
- 退货入库单、售后端到端验证（`task_plan.md` 119-120 行）
- 微信二次提审/审核、小程序发版（`task_plan.md` 141-143、476、507 行）
- 通知中心（模板表、站内信、短信、重试）（`task_plan.md` 236-241 行）
- 积分体系、秒杀（`task_plan.md` 249-261 行）
- AI Function Calling / 商品卡片 / RAG（`task_plan.md` 154-157 行）

**运维**
- EdgeOne 缓存规则（商城 404 的根因）、WebSocket 加速、证书续签、告警 webhook（`task_plan.md` 290、323-326 行）
- 静态资源迁 CDN、WAF/高防（`task_plan.md` 171-172 行）
- RabbitMQ 跨实例广播、未支付延迟队列（`task_plan.md` 180-181、218-220 行）
