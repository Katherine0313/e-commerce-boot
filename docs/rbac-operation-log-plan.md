# RBAC 操作日志 / 登录日志 实施方案

> 生成日期：2026-11-04
> 依据：当前代码（common / auth-service / order-service / aftersale-service）、本地数据库表结构，以及根目录 `findings.md`、`progress.md`、`task_plan.md` 的现状记录。

---

## 一、结论先行

操作日志的**基建已经做了一半**：`operation_log` 表、`@OperationLog` 注解、切面、异步写入器都在，也已经在售后审核、快递模板、发货等 8 个方法上打点。真正缺的是「可查、可看、可追溯」的闭环：

1. 没有查询接口，后台也没有页面 —— 日志只能直接翻数据库。
2. 没有登录日志 —— 登录成功/失败/锁定/停用都查不到，账号爆破和误锁定无法审计。
3. 日志只存 `user_id`，没有 `username` 快照 —— 账号被删除或改名后无法识别操作人。
4. `params` 原样落库 —— 重置密码、手机号等敏感参数有泄露风险。
5. IP 提取口径不统一 —— 登录接口已改用 `EO-Connecting-IP`，日志采集器还在用 `X-Forwarded-For`。
6. 建表 SQL 没有入库 —— 当前 `docs/sql/` 下找不到 `operation_log` 的 DDL，新环境无法复现。
7. 打点覆盖窄 —— RBAC 自身（账号/角色/菜单）的增删改完全没有审计。
8. 没有保留策略 —— 表会无限增长。

建议分三期：**P0 查询闭环 + 登录日志 → P1 扩大打点覆盖 → P2 归档/导出/保留策略**。

---

## 二、现状盘点

### 2.1 已具备的能力

| 能力 | 位置 | 说明 |
|---|---|---|
| `operation_log` 表 | 本地/生产 `e_commerce_db` | 14 个字段，已有 `user_id`、`module`、`created_at` 三个索引；本地仅 3 行 |
| `@OperationLog` 注解 | `common/src/main/java/ecommerce/log/OperationLog.java` | 属性：`module`、`action`、`bizId`(SpEL)、`detail`(SpEL)、`recordParams` |
| 采集切面 | `common/.../log/OperationLogAspect.java` | `@Order(LOWEST_PRECEDENCE)`，`finally` 中提交，失败只 warn |
| 采集工具 | `common/.../log/OperationLogCollector.java` | 取 `UserContext` 的 userId/userType、IP、参数 JSON（截断 2000 字符） |
| 异步写入 | `common/.../log/JdbcOperationLogRecorder.java` | `JdbcTemplate` + 专用线程池；无 `JdbcTemplate` 的服务（file-service）自动跳过 |
| 已有打点 | 8 处 | 售后审核/删除 2 处；快递模板增改删 3 处；发货/重发/改物流 3 处 |

### 2.2 缺口与影响

| 缺口 | 影响 | 优先级 |
|---|---|---|
| 无查询接口、无后台页面 | 日志不可用，出了事只能连库查 | P0 |
| 无登录日志表 | 爆破、锁定、停用无法追溯 | P0 |
| 无 `username` 快照 | 账号删除/改名后日志变成匿名 | P0 |
| `params` 未脱敏 | 密码、手机号等敏感信息落库 | P0 |
| IP 口径不一致 | 代理后记录的是内网/代理 IP | P0 |
| DDL 未入库 | 新环境漏建表，日志静默丢失（只 warn） | P0 |
| RBAC 自身无打点 | 「谁改了谁的权限」查不到 | P1 |
| 无保留策略 | 表无限增长，查询变慢 | P2 |

### 2.3 关键代码事实

- `UserContext` 只有 `userId`、`userType`、`roles`、`permissions`，**没有 username**；但管理员 JWT 已经带了 username claim（`JwtUtil.generateAdminToken(id, username, roles, perms)`），补一个 ThreadLocal 字段即可。
- `OperationLogCollector.clientIp()` 只认 `X-Forwarded-For` / `X-Real-IP` / `remoteAddr`，与 `AuthController.resolveClientIp()` 的 `EO-Connecting-IP` 优先级不一致。
- `AuthService.login()` 的失败分支有五类：参数为空、已被锁定、验证码错误、用户名或密码错误、账号停用；成功分支在 `buildLoginResult()`。登录日志需要覆盖这些分支。
- `AuthController.logout()` 当前是无状态 JWT，直接返回成功，没有任何记录。

---

## 三、数据模型设计

### 3.1 `operation_log` 调整

```sql
-- 1) 操作人快照：账号删除/改名后仍可识别
ALTER TABLE operation_log
  ADD COLUMN username VARCHAR(64) NULL COMMENT '操作人账号快照' AFTER user_id;

-- 2) 查询索引（现有 user_id / module / created_at 单列索引可保留）
ALTER TABLE operation_log
  ADD INDEX idx_oplog_created (created_at),
  ADD INDEX idx_oplog_module_action (module, action, created_at),
  ADD INDEX idx_oplog_username (username, created_at);
```

> `detail` 仍是 `varchar(255)`。P1 若要做「改价前后值」快照，建议在 `detail` 里写短文本（如 `price 199→179`），不要把完整对象塞进去。

### 3.2 新建 `admin_login_log`

```sql
CREATE TABLE admin_login_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  admin_id    BIGINT       NULL COMMENT '管理员 ID，账号不存在时为 NULL',
  username    VARCHAR(64)  NOT NULL COMMENT '登录名（原样保留，便于排查爆破）',
  event       VARCHAR(16)  NOT NULL COMMENT 'LOGIN / LOGOUT',
  success     TINYINT      NOT NULL DEFAULT 0 COMMENT '1 成功 0 失败',
  reason      VARCHAR(128) NULL COMMENT '失败原因：验证码错误/密码错误/账号停用/锁定中/参数为空',
  ip          VARCHAR(64)  NULL COMMENT '客户端 IP',
  user_agent  VARCHAR(255) NULL COMMENT 'User-Agent',
  created_at  DATETIME     NOT NULL COMMENT '发生时间',
  PRIMARY KEY (id),
  KEY idx_loginlog_username (username, created_at),
  KEY idx_loginlog_created (created_at),
  KEY idx_loginlog_ip (ip, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台登录/登出日志';
```

### 3.3 容量估算

后台账号约 5 个、日均登录几十次，登录日志一年不到 10 万行。操作日志量取决于 P1 打点范围，按每天 2 千条估算，一年约 70 万行，单表可控；保留 180 天后清理即可。

---

## 四、后端改造

### 4.1 common 公共层

| 改动 | 文件 | 内容 |
|---|---|---|
| 增加 username 上下文 | `common/.../utils/UserContext.java` | 新增 `CURRENT_USERNAME` ThreadLocal、`setUsername/getUsername`，`remove()` 一并清理 |
| 令牌解析带 username | `common/.../interceptor/JwtInterceptor.java` | 从 claims 读取 `username` 写入 `UserContext` |
| 采集器取 username | `common/.../log/OperationLogCollector.java` | 新增 `currentUsername()` |
| IP 口径统一 | 同上 `clientIp()` | 顺序改为 `EO-Connecting-IP` → `X-Forwarded-For` 首段 → `X-Real-IP` → `remoteAddr` |
| 参数脱敏 | 同上 `paramsJson()` | 序列化后正则替换：`password/pwd/token/secret` 值 → `***`；11 位手机号 → `138****5678`；再截断 |
| 条目加字段 | `common/.../log/OperationLogEntry.java` | 增加 `username` |
| 写入加字段 | `common/.../log/JdbcOperationLogRecorder.java` | INSERT 增加 `username`（与 3.1 的列顺序一致） |

### 4.2 auth-service 登录日志

新增：

- `entity/AdminLoginLog.java`
- `mapper/AdminLoginLogMapper.java` + `resources/mappers/auth/AdminLoginLogMapper.xml`（`insert`、分页查询、`count`）
- `service/LoginLogService.java`（`record(username, adminId, event, success, reason, ip, userAgent)`，内部 try/catch，**写失败只 warn，绝不影响登录**）

打点位置：

| 位置 | event | success | reason |
|---|---|---|---|
| `login()` 参数为空 | LOGIN | 0 | 用户名和密码不能为空 |
| `assertNotLocked()` 抛锁定 | LOGIN | 0 | 登录失败次数过多，已锁定 |
| `validateCaptcha()` 抛错 | LOGIN | 0 | 验证码错误/已过期 |
| 账密不匹配 | LOGIN | 0 | 用户名或密码错误 |
| 账号停用 | LOGIN | 0 | 账号已停用 |
| `buildLoginResult()` 成功后 | LOGIN | 1 | NULL |
| `AuthController.logout()` | LOGOUT | 1 | NULL（能取到 `UserContext` 时记录 adminId/username） |

> 实现方式：登录日志量小，直接在方法内显式调用 `LoginLogService`（同步 insert），不用 AOP，原因是失败原因分散在多个异常分支，AOP 拿不到业务语义。

### 4.3 查询接口（auth-service）

放在 auth-service 的原因：系统管理（账号/角色/菜单）已经在这里，且所有服务共用同一个 `e_commerce_db`，不需要新建服务或跨服务调用。

| 接口 | 权限码 | 说明 |
|---|---|---|
| `GET /api/admin/system/logs/operation` | `system:log:operation:list` | 操作日志分页 |
| `GET /api/admin/system/logs/login` | `system:log:login:list` | 登录日志分页 |

操作日志查询参数：

| 参数 | 类型 | 说明 |
|---|---|---|
| `page` / `pageSize` | int | 默认 1 / 20，`pageSize` 上限 100 |
| `module` | string | 精确匹配 |
| `action` | string | 精确匹配 |
| `username` | string | 模糊匹配 |
| `success` | 0/1 | 成功/失败 |
| `startTime` / `endTime` | datetime | 不传默认近 7 天，避免全表扫描 |
| `keyword` | string | 匹配 `detail` / `uri` / `biz_id` |

返回字段：`id, username, userType, module, action, bizId, detail, method, uri, ip, params, success, errorMsg, costMs, createdAt`。

登录日志查询参数：`page/pageSize/username/event/success/ip/startTime/endTime`；返回 `id, username, adminId, event, success, reason, ip, userAgent, createdAt`。

实现约定：MyBatis Mapper + `LIMIT #{offset}, #{pageSize}`（生产 MySQL 5.7）；`params` 在 VO 里保持 JSON 字符串，前端详情弹窗做格式化展示。

### 4.4 权限与菜单种子数据

```sql
-- 父菜单：系统管理（按实际 id 取，下面用变量占位）
SET @parent_id = (SELECT id FROM admin_menu WHERE menu_name = '系统管理' AND parent_id = 0 LIMIT 1);

INSERT INTO admin_menu (parent_id, menu_name, menu_type, path, perms, icon, sort_order, status)
VALUES
  (@parent_id, '操作日志', 'C', '/system/logs/operation', 'system:log:operation:list', 'icon-file-text', 90, 1),
  (@parent_id, '登录日志', 'C', '/system/logs/login',     'system:log:login:list',     'icon-login',     91, 1);

-- 只授权给超管角色
INSERT INTO admin_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM admin_role r, admin_menu m
WHERE r.role_key = 'admin' AND m.perms IN ('system:log:operation:list','system:log:login:list');
```

### 4.5 P1：扩大打点覆盖

| 模块 | 目标方法 | module / action | 备注 |
|---|---|---|---|
| RBAC 账号 | 新增/编辑/删除/重置密码 | `system` / `USER_ADD` `USER_EDIT` `USER_REMOVE` `RESET_PWD` | 重置密码 `recordParams = false` |
| RBAC 角色 | 新增/编辑/删除/分配菜单 | `system` / `ROLE_ADD` `ROLE_EDIT` `ROLE_REMOVE` `ROLE_ASSIGN` | detail 记录角色名 |
| RBAC 菜单 | 新增/编辑/删除 | `system` / `MENU_ADD` `MENU_EDIT` `MENU_REMOVE` | detail 记录菜单名 |
| 商品 | 新增/编辑/上下架/改价/库存调整 | `product` / `PRODUCT_ADD` `PRODUCT_EDIT` `SHELF` `PRICE_CHANGE` `STOCK_ADJUST` | 改价 detail 写「原价→新价」 |
| 订单 | 取消/关闭/改备注 | `order` / `CANCEL` `CLOSE` `REMARK` | 退款走售后日志，不重复 |
| 营销 | Banner/优惠券/文章 增删改 | `promotion` / `BANNER_*` `COUPON_*` `ARTICLE_*` | — |

原则：只对「会改变业务状态或权限」的写操作打点；查询类不打点，避免日志洪水。

### 4.6 P2：治理

- 保留策略：auth-service 增加每日 03:00 定时任务，分批删除 `created_at < NOW() - INTERVAL 180 DAY`（每批 1000 行，避免长事务锁表）。
- 导出：查询接口增加 `export=true` 时流式输出 CSV（上限 5 万行），只允许超管。
- 告警（可选）：同一 IP 5 分钟内 ≥10 次不同用户名登录失败 → warn 日志 + webhook（与 `healthcheck.env` 的告警通道复用）。

---

## 五、前端改造（e-commerce 后台）

| 改动 | 文件 | 内容 |
|---|---|---|
| API | `src/api/system.js` | `getOperationLogs(params)`、`getLoginLogs(params)` |
| 操作日志页 | `src/pages/system/logs/OperationLogList.vue` | 筛选（模块/动作/账号/结果/时间范围）、分页表格、详情抽屉（params 格式化 JSON）、无编辑/删除 |
| 登录日志页 | `src/pages/system/logs/LoginLogList.vue` | 筛选（账号/事件/结果/IP/时间）、分页表格；失败原因标红 |
| 路由 | `src/router/index.js`（系统管理分组，约 317 行处） | 增加 `/system/logs/operation`、`/system/logs/login`，`meta.permission` 对应 4.4 权限码 |
| 按钮权限 | 现有 `v-permission` 指令 | 页面只读，不需要操作按钮；如做导出，用 `system:log:operation:export` |

交互要求：

- 默认时间范围「近 7 天」，避免首屏全表扫描。
- `module`/`action` 用下拉（从字典/接口 distinct 返回），不要手输。
- 详情抽屉展示请求参数与错误信息，`params` 为空显示「—」。
- 列表不提供删除按钮（审计日志只读，清理交给定时任务）。

---

## 六、实施顺序与验收

| 阶段 | 内容 | 验收标准 |
|---|---|---|
| P0-1 数据 | 3.1 ALTER + 3.2 建表 + 4.4 菜单种子，DDL 入库 `docs/sql/log/` | 本地/生产表结构一致；`SHOW COLUMNS` 有 `username`；菜单可见 |
| P0-2 common | UserContext/JwtInterceptor/Collector/Entry/Recorder | 新日志写入带 username；重置密码后查库无明文密码 |
| P0-3 登录日志 | LoginLogService + 5 个失败分支 + logout | 正确密码 1 条 success=1；错误密码 1 条 success=0 且 reason 正确 |
| P0-4 查询接口 | 两个 GET 接口 + 权限码 | 超管 200；operator 403；无 token 401 |
| P0-5 前端 | 两个只读页面 + 路由 | 筛选/分页/详情可用；夜间模式样式正常 |
| P1 打点 | 4.5 清单 | 每个动作操作后能在页面查到对应记录 |
| P2 治理 | 定时清理 + 导出 + 告警 | 清理任务 dry-run 日志正确；导出有上限 |

**发布顺序**：先执行 DDL（新增列可空、新表独立）→ 发 `auth-service`（新接口/登录日志）→ 发相关业务服务（P1 打点）→ 最后发后台前端（`release-web.sh`）。
**回滚**：前端移除路由即可；后端日志写入全部 try-catch，不阻塞业务；新表可 `RENAME TABLE` 留档后再删。

---

## 七、风险与注意事项

1. **异步写入可能丢日志**：线程池队列满或服务优雅停机时，队列里的条目会丢。当前定位是「丢了不影响业务」，可以接受；若审计要求强一致，需要改成同步写或落本地文件。
2. **参数脱敏不能覆盖所有结构**：脱敏是字符串级正则，嵌套对象/数组里的敏感字段可能漏。最稳妥的是对重置密码、改密码等接口直接 `recordParams = false`。
3. **大表查询**：接口强制默认 7 天 + `pageSize ≤ 100`，否则 MySQL 5.7 上会扫全表。
4. **file-service 无 JdbcTemplate**：当前文件上传不会产生操作日志。若需要审计上传行为，得给 file-service 单独接数据源或加专用记录器。
5. **多服务写同一张表**：依赖「所有服务共用 `e_commerce_db`」这一现状；若未来按服务拆库，需要改成日志服务统一收集。
6. **时间口径**：日志时间由 Java `LocalDateTime.now()` 生成，与数据库 `NOW()` 可能有时区差；查询接口统一用应用时间，不混用。

---

## 八、附录：三份文档中的其他未完成项（与本次方案无关，供排期参考）

> 状态可能已被后续发布覆盖，落地前需逐条复核。

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
- EdgeOne 缓存规则（本次商城 404 的根因）、WebSocket 加速、证书续签、告警 webhook（`task_plan.md` 290、323-326 行）
- 静态资源迁 CDN、WAF/高防（`task_plan.md` 171-172 行）
- RabbitMQ 跨实例广播、未支付延迟队列（`task_plan.md` 180-181、218-220 行）

---

## 九、需要你拍板的点

1. 操作日志、登录日志保留多久？建议 180 天。按180天
2. 日志页面是否只给「店铺管理员」角色？建议是。是的
3. 是否需要 CSV 导出？如果需要，导出权限单独开一个权限码。需要，外加excel导出
4. 是否要记录 C 端用户（小程序/商城）的敏感操作？当前 `operation_log` 的 `user_type` 已能区分，但 C 端量大会显著增加日志量，建议先不做。不做，主要记录后台管理系统的登录用户的操作情况。
5. `params` 里的手机号是否也要脱敏？建议按 `138****5678` 处理。是的

