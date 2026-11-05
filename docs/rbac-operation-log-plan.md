# RBAC 操作日志 / 登录日志 实施方案

> 2026-11-04 初版，2026-11-05 按本地库 DDL 与源码逐条核对后精简修订。只借鉴 `docs/芋道（yudao）日志体系完整梳理.docx` 的做法（DIFF、脱敏、导出、保留策略），不引入它的框架和表名。

---

## 一、做什么

只做两类，都是**后台管理员**的审计日志。不做 C 端日志、不做 API 访问日志、不做接口异常堆栈表。

### 1. 操作日志（表 `admin_operation_log`，已存在）

记管理员「改了什么东西」，**只记写操作**，查询不记。

| 模块 | 要覆盖的动作 | 现状 |
|---|---|---|
| 售后 | 审核、删除 | ✅ 已打点 2 处 |
| 发货 | 发货、补发、改物流 | ✅ 已打点 3 处 |
| 快递公司 | 增/改/删 | ✅ 已打点 3 处 |
| 管理员账号 | 增/改/删/重置密码 | ❌ 待补 |
| 角色 | 增/改/删/分配菜单 | ❌ 待补 |
| 菜单 | 增/改/删 | ❌ 待补 |
| 商品 | 增/改/删（含上下架、改价、库存字段） | ❌ 待补 |
| 库存预警 | 预警开关、阈值 | ❌ 待补 |
| 订单 | 改收货信息与备注、取消、删除、批量删除 | ❌ 待补 |
| 营销 | Banner/文章（product-service）、优惠券（order-service） | ❌ 待补 |

> 售后已有 `after_sale_log` 业务流水表（状态流转），那是业务对象自己的轨迹，与通用审计日志职责不同，**不合并、不重复造**。

### 2. 登录日志（表 `admin_login_log`，需新建）

记管理员「登录/登出」本身。`admin` 表没有 `last_login_time` 之类字段，所以必须独立建表。

- `login_type`：`LOGIN` / `LOGOUT`
- `result_code`：`SUCCESS` / `BAD_CREDENTIALS` / `USER_DISABLED` / `CAPTCHA_ERROR` / `LOCKED` / `PARAM_MISSING`
- 附带：账号、IP、User-Agent、失败原因、时间

### 顺序

**P0 补字段与脱敏 → P0 登录日志 → P0 查询接口与页面 → P1 打点覆盖与变更内容 → P2 导出与清理**

---

## 二、现状（2026-11-05 实测，已核对）

### 2.1 `admin_operation_log` 表

14 个字段：`id / user_id / user_type / module / action / biz_id / detail(255) / method / uri / ip / params(2000) / success / error_msg / cost_ms / created_at`。

- 索引：`module`、`user_id`、`created_at` 三个单列索引；整表 `utf8mb4 / utf8mb4_general_ci`（MySQL 5.7，新表保持一致）
- 本地库**当前 3 条**（`AUTO_INCREMENT=61`，历史写入过约 60 条后被清过）；新环境是空表
- **DDL 不在仓库**：`docs/sql/` 下没有它的建表脚本

### 2.2 现有 8 处打点

`AdminAfterSaleServiceImpl.review/delete` → `aftersales/REVIEW`、`aftersales/DELETE`；`OrderDeliveryServiceImpl.ship/resend/updateDelivery` → `delivery/SHIP、RESEND、UPDATE_DELIVERY`；`DeliveryExpressServiceImpl.save/update/remove` → `delivery/EXPRESS_SAVE、EXPRESS_REMOVE`。

注解打在 **Service 实现类**上；切面 `LOWEST_PRECEDENCE`，在事务内层，业务回滚时日志标 `success=false`。

### 2.3 菜单现状（`admin_menu`）

| id | 名称 | 类型 | 路径 | 权限码 |
|---|---|---|---|---|
| 800 / 801 / 802 | 管理员账号 / 角色管理 / 菜单管理 | C | `/system/users` 等 | `system:user:list` 等 |
| 803 | 操作日志 | C | `/system/logs` | `system:log:list` ← **有菜单无实现** |

`admin_menu.MAX(id)=838`，**804 / 839 / 840 空闲可用**。F 行（按钮）的 `path` 为 NULL、`visible=1`。侧边栏由后端 `buildMenuTree()` 下发，F 行不出现在菜单里。

### 2.4 关键代码事实

- 日志包真实路径 `common/src/main/java/ecommerce/log/`（`OperationLog` / `OperationLogAspect` / `OperationLogCollector` / `OperationLogEntry` / `OperationLogRecorder` / `JdbcOperationLogRecorder` / `LogAsyncConfig`）
- `UserContext` 只有 `userId/userType/roles/permissions`，**没有 username**；但 JWT 里**已有 username claim**（`JwtUtil.generateAdminToken` 写了），`JwtInterceptor` 只是没取
- `JwtInterceptor` 只管 `/api/admin` 前缀，登录接口是公开的 → **登录日志必须显式记**；`AuthController.logout()` 无状态 JWT，直接返回、不记任何东西
- `AuthServiceImpl.login()` 真实分支顺序：**参数空 → 已锁定 → 验证码错 → 账密错（同时计数，达上限抛锁定）→ 密码正确后才判账号停用 → 成功**
- `AuthServiceApplication` **没有 `@EnableScheduling`**（order-service 有）→ P2 定时清理要补
- **网关已把 `/api/admin/system/**` 路由到 auth-service** → 新增查询接口**不用改网关**
- **JSON 命名**：项目无全局蛇形配置。product-service 的 VO 单独加了 `@JsonNaming(SnakeCase)`，auth-service 的 VO 是**驼峰** → 日志 VO 用驼峰，**不要加** `@JsonNaming`
- 前端已有 `v-permission` 指令、`src/utils/mask.js` 的 `maskPhone()`、`src/api/system.js`；`src/pages/system/` 下只有 `menus/roles/users`（**没有 logs**）；`src/router/index.js` 的 `/system` 分组里**没有 `/system/logs`**
- 父 pom 已管 `poi-ooxml` 6.0.0，auth-service 引依赖即可；`common` 引 `spring-jdbc` + 各服务有 DataSource → `JdbcTemplate` 自动配置可用，auth-service 打点能正常写库

---

## 三、数据模型

### 3.1 `admin_operation_log` 增量（不动存量）

```sql
ALTER TABLE admin_operation_log
  ADD COLUMN username VARCHAR(64) NULL COMMENT '操作人账号快照' AFTER user_id;

ALTER TABLE admin_operation_log
  MODIFY COLUMN detail VARCHAR(1024) NULL COMMENT '补充说明/变更详情';

ALTER TABLE admin_operation_log
  ADD INDEX idx_operation_log_module_action (module, action, created_at),
  ADD INDEX idx_operation_log_username (username, created_at);
```

### 3.2 新建 `admin_login_log`

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

> `COLLATE=utf8mb4_general_ci` 必须显式写：服务器是 MySQL 5.7，不支持 `utf8mb4_0900_ai_ci`。

### 3.3 SQL 入库

仓库约定是 `docs/sql/` 下扁平命名 `yyyyMMdd_描述.sql`。新增 4 个：`20261105_operation_log_schema.sql`（§2.1 真实 DDL 回写）、`20261105_operation_log_add_username.sql`（§3.1）、`20261105_admin_login_log.sql`（§3.2）、`20261105_log_menu_seed.sql`（§4.5）。

**本地执行后必须在生产执行同一份 SQL**，不允许只改本地。

---

## 四、后端改造

### 4.1 common（`common/src/main/java/ecommerce/`）

| 文件 | 改动 |
|---|---|
| `utils/UserContext.java` | 新增 `CURRENT_USERNAME` ThreadLocal + `setUsername/getUsername`，`remove()` 一并清理 |
| `interceptor/JwtInterceptor.java` | `preHandle` 里读 `claims.get("username")` 写入 `UserContext` |
| `log/OperationLogCollector.java` | 新增 `currentUsername()`；`clientIp()` 改为 `EO-Connecting-IP` → `X-Forwarded-For` 首段 → `X-Real-IP` → `remoteAddr` |
| `log/OperationLogCollector.paramsJson()` | `password/pwd/token/secret` → `***`；11 位手机号 → `138****5678`；仍截断 2000 字符 |
| `log/OperationLogEntry.java`、`log/JdbcOperationLogRecorder.java` | 加 `username` 字段与 INSERT 列（**手写 SQL，别忘了同步加列**） |
| 新增 `log/LogDetail.java`、`log/LogDiff.java` | 见 §4.2 |

### 4.2 变更内容（不引注解扫描）

`LogDetail`：ThreadLocal 累加器，Service 里放变更文案；切面在 `detail` SpEL 为空时回退取 `LogDetail.join()`，`finally` 清理。

`LogDiff` 只提供两个方法：

```java
LogDiff.of("销售价", 199, 179);                       // 【销售价】从【199】修改为【179】
LogDiff.of("售后状态", 1, 2, AfterSaleStatusText::of); // 翻译成中文
```

约束：拿不到旧值就写纯文本，不硬凑 DIFF；密码、手机号、身份证不进 DIFF；商品要**只列真正变化的字段**，不要把整个 DTO 塞进 detail。

### 4.3 登录日志（auth-service）

新增：`entity/AdminLoginLog`、`mapper/AdminLoginLogMapper` + `resources/mappers/auth/AdminLoginLogMapper.xml`、`service/LoginLogService`、`enums/LoginType` + `LoginResult`。

打点位置（按 `AuthServiceImpl.login()` 真实分支）：

| 位置 | result_code |
|---|---|
| 参数为空 | `PARAM_MISSING` |
| `assertNotLocked()` 抛锁 / 失败次数达上限抛 429 | `LOCKED` |
| `validateCaptcha()` 抛错 | `CAPTCHA_ERROR` |
| 账号不存在 / 密码不匹配 | `BAD_CREDENTIALS` |
| 账号停用（密码正确**之后**才判） | `USER_DISABLED` |
| `buildLoginResult()` 成功 / `AuthController.logout()` | `SUCCESS`（后者 login_type=`LOGOUT`） |

实现要点：

- 显式调用 `LoginLogService.record(...)`，**同步 insert**（登录量小），不用 AOP —— 失败原因分散在多个分支，AOP 拿不到语义
- 记录器内部必须 try/catch：**写登录日志失败绝不能把管理员挡在门外**
- `username` 记用户输入的原样 trim 值（允许账号不存在），便于查爆破；账号不存在时 `admin_id` 留空
- IP 复用 `AuthController.resolveClientIp()` 的口径（`EO-Connecting-IP` 优先）

### 4.4 查询接口（auth-service）

| 接口 | 权限码 |
|---|---|
| `GET /api/admin/system/logs` | `system:log:list`（复用 803） |
| `GET /api/admin/system/login-logs` | `system:log:login:list` |
| `GET /api/admin/system/logs/export?format=csv\|xlsx` | `system:log:export` |
| `GET /api/admin/system/login-logs/export?format=csv\|xlsx` | `system:log:login:export` |

新增 `AdminOperationLogMapper` + XML（读 `admin_operation_log`，与写入方共用 `e_commerce_db`）。Controller 上加 `@RequirePerm`，与 `AdminSystemUserController` 写法一致；超管靠 `*:*:*` 放行。

- 操作日志参数：`page/pageSize/module/action/username/success/startTime/endTime/keyword`，默认近 7 天，`pageSize ≤ 100`，SQL 用 `LIMIT #{offset}, #{pageSize}`（MySQL 5.7）
- 登录日志参数：`page/pageSize/username/loginType/resultCode/ip/startTime/endTime`
- 返回字段用**驼峰**（`createdAt`、`userType`…），**不要加 `@JsonNaming(SnakeCase)`**

### 4.5 菜单与权限种子

```sql
INSERT INTO admin_menu (id, parent_id, menu_name, menu_type, path, perms, icon, sort_order, status, visible)
VALUES (804, 8, '登录日志', 'C', '/system/logs/login', 'system:log:login:list', 'icon-login', 5, 1, 1);
INSERT INTO admin_menu (id, parent_id, menu_name, menu_type, perms, sort_order, status, visible) VALUES
(839, 803, '日志导出', 'F', 'system:log:export', 1, 1, 1),
(840, 804, '日志导出', 'F', 'system:log:login:export', 1, 1, 1);
INSERT INTO admin_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM admin_role r JOIN admin_menu m ON m.id IN (804, 839, 840) WHERE r.role_key = 'admin';
```

### 4.6 P1 打点覆盖（真实类名与权限码）

| 模块 | 类 / 方法 | module / action | Controller 权限码 |
|---|---|---|---|
| 管理员账号 | `auth-service/.../AdminAccountServiceImpl`：`create/update/delete/resetPassword` | `system` / `USER_ADD` `USER_EDIT` `USER_REMOVE` `RESET_PWD` | `system:user:add` `:edit` `:remove` `:resetPwd` |
| 角色 | `auth-service/.../AdminRoleServiceImpl`：`create/update/delete/assignMenus` | `system` / `ROLE_ADD` `ROLE_EDIT` `ROLE_REMOVE` `ROLE_ASSIGN` | `system:role:add` `:edit` `:remove` `:assign` |
| 菜单 | `auth-service/.../AdminMenuServiceImpl`：`create/update/delete` | `system` / `MENU_ADD` `MENU_EDIT` `MENU_REMOVE` | `system:menu:add` `:edit` `:remove` |
| 商品 | `product-service/.../AdminProductServiceImpl`：`create/update/delete` | `product` / `PRODUCT_ADD` `PRODUCT_EDIT` `PRODUCT_REMOVE` | `product:product:add` `:edit` `:remove` |
| 库存预警 | `product-service/.../AdminStockServiceImpl`：`create` | `product` / `STOCK_ALERT` | `product:stock:edit` |
| 订单 | `order-service/.../AdminOrderServiceImpl`：`updateOrder/cancelOrder/deleteOrder/batchDelete` | `order` / `ORDER_UPDATE` `ORDER_CANCEL` `ORDER_REMOVE` `ORDER_BATCH_REMOVE` | `order:order:update` `:update` `:remove` `:remove` |
| 优惠券 | `order-service/.../AdminCouponServiceImpl`：`create/update/stop/delete/grant` | `promotion` / `COUPON_ADD` `COUPON_EDIT` `COUPON_STOP` `COUPON_REMOVE` `COUPON_GRANT` | `business:coupon:add` `:edit` `:edit` `:remove` `:grant` |
| Banner | `product-service/.../AdminBannerServiceImpl`：增改删 + 上下架 | `promotion` / `BANNER_ADD` `BANNER_EDIT` `BANNER_STATUS` `BANNER_REMOVE` | `business:banner:add` `:edit` `:edit` `:remove` |
| 文章 | `product-service/.../AdminArticleServiceImpl`：增改删 + 上下架 | `promotion` / `ARTICLE_ADD` `ARTICLE_EDIT` `ARTICLE_STATUS` `ARTICLE_REMOVE` | `business:article:add` `:edit` `:edit` `:remove` |

**商品模块注意**：后台商品只有 create/update/delete 三个写接口，上下架（`listingStatus`）、改价（`salesPrice`）、库存（`stock`）**都在 `update` 一个方法里**。所以**一次 update 只打一条日志**，DIFF 里按实际变化的字段逐项列出。要按动作拆成 `PRODUCT_SHELF` / `PRODUCT_PRICE` 需在 Service 里按字段分流，先不做。

**订单模块注意**：后台**没有「关闭订单」接口**（只有 list/get/update/ship/cancel/delete/batch-delete/logistics）。

原则：只打改变业务状态或权限的写操作；查询、列表、详情、导出不打点；商品 `specs` JSON 只记规格项数量与名称。

### 4.7 P2 导出与清理

- CSV 直接写流，`text/csv;charset=UTF-8` + `Content-Disposition`
- Excel 用 `SXSSFWorkbook`（窗口 100 行），auth-service 引 `poi-ooxml`（版本走父 pom）
- 超过 5 万行返回 400，提示缩小时间范围
- **CSV/Excel 公式注入防护**：以 `= + - @` 开头的单元格加前缀 `'`
- 导出动作本身记一条 `system / LOG_EXPORT`
- 清理：`AuthServiceApplication` 补 `@EnableScheduling`，每日 03:00 每批 1000 行删 `created_at < NOW() - INTERVAL 180 DAY`，记 `system / LOG_CLEAN`；删失败只 warn

---

## 五、前端（`e-commerce`）

| 改动 | 文件 |
|---|---|
| API | `src/api/system.js` 加 `getOperationLogs`、`getLoginLogs`、两个 blob 导出方法 |
| 操作日志页 | 新建 `src/pages/system/logs/OperationLogList.vue` |
| 登录日志页 | 新建 `src/pages/system/logs/LoginLogList.vue` |
| 路由 | `src/router/index.js` 的 `/system` 分组加 `logs`、`logs/login`，**路径必须与 `admin_menu.path` 一致** |
| 导出 | 两页用 `v-permission="'system:log:export'"` / `'system:log:login:export'`，`responseType: 'blob'` |
| 脱敏 | 复用 `src/utils/mask.js` 的 `maskPhone()` |

要点：

- 页面结构照抄 `src/pages/system/users/UserList.vue`（`a-form` 筛选 + `a-table` + `a-modal`）；**表格列宽不写死**（不写 `width`、不写 `:scroll`、不用 `fixed` 列，交给 antd auto 布局）
- 后端返回**驼峰字段**，前端直接用，不做蛇形转换
- 默认近 7 天；`module/action` 下拉来自常量，不手输；详情抽屉空值显示「—」；不提供删除按钮

---

## 六、实施顺序与验收

| 阶段 | 内容 | 验收 |
|---|---|---|
| P0-1 数据 | §3.1、§3.2、§4.5，4 个 SQL 入库 | 本地 `SHOW COLUMNS` 一致；804/839/840 菜单出现；**生产执行同一份 SQL** |
| P0-2 common | UserContext / JwtInterceptor / Collector / Entry / Recorder | 新日志有 username；重置密码查库无明文；手机号 `138****5678`；IP 取到 EdgeOne 真实 IP |
| P0-3 登录日志 | LoginLogService + 各类失败结果 + logout | 正确密码 SUCCESS；错误密码 BAD_CREDENTIALS；连错第 5 次 LOCKED；验证码错 CAPTCHA_ERROR；停用账号 USER_DISABLED；**写日志失败不影响登录** |
| P0-4 查询接口 | 4 个接口 + 权限码 | 超管 200；operator 403；无 token 401 |
| P0-5 前端 | 两个页面 + 路由 | 侧边栏「操作日志」「登录日志」能点开且有数据；样式与其它系统页一致 |
| P1-1 变更内容 | `LogDetail` + `LogDiff` + 2~3 处改造 | 售后审核 detail 显示「售后状态 待审核→审核通过」；商品改价显示「销售价 199→179」 |
| P1-2 打点 | §4.6 清单 | 每个动作能查到记录且带变更内容；`params` 里搜不到密码明文 |
| P2 | CSV + Excel + 定时清理 | 两种格式行数一致；超 5 万行返回 400；清理 dry-run 正确；CSV 无公式注入 |

发布顺序：**DDL（含生产）→ auth-service（接口/登录日志/导出）→ common + 业务服务（P1 打点）→ 后台前端**。

回滚：前端移除路由；日志写入全部 try-catch 不阻塞业务；新表可 `RENAME` 留档。

---

## 七、风险

1. **异步写可能丢日志**：线程池满或停机时队列条目会丢，当前定位是「丢了不影响业务」，审计要求强一致再改同步
2. **`LogDetail` 是 ThreadLocal**：必须在业务方法内 `add`，切面 `finally` 清理，跨线程不继承
3. **敏感字段**：`params` 只做字符串级脱敏，嵌套可能漏；重置密码等接口直接 `recordParams = false`
4. **大表查询**：默认 7 天 + `pageSize ≤ 100`，否则 MySQL 5.7 扫全表
5. **导出内存**：必须 `SXSSFWorkbook`，禁止 5 万行先入 List；**CSV/Excel 公式注入**要加前缀防护（`params`/`detail` 可能被构造成 `=cmd`）
6. **file-service 无 JdbcTemplate**：文件上传不产生操作日志
7. **多服务共用 `e_commerce_db`**：日志读写都依赖这一点，将来拆库要改独立日志服务
8. **时间口径**：日志时间由 Java 生成，不与 DB `NOW()` 混用（服务器时区是 UTC，`NOW()` 比 Java 早 8 小时）
9. **登录日志不能挡登录**：记录器全 try-catch
10. **JSON 命名别混用**：日志 VO 在 auth-service，用驼峰
11. **菜单 id 冲突**：新增前先查 `admin_menu.MAX(id)`（本项目 id 段 800+ 与 300+ 混用）

---

## 八、已定决策与待确认

**已定**：保留 180 天定时清理；页面仅超管可见；CSV + Excel 都要且独立权限码；只记后台管理员；`params` 手机号脱敏为 `138****5678`；表名改为 `admin_operation_log`（后台专用统一 admin_ 前缀，原 `operation_log` 重命名）、`admin_login_log` 新建；查询接口放 auth-service（网关已路由，不改网关）。

**待确认**：

1. 商品字段级 action 分类要不要做（区分改价/上下架）
2. 日志页是否支持按 `bizId` 跳到售后单/订单详情（需要各模块提供详情路由）
3. 导出是否需要包含已删除账号的历史记录（`username` 快照可以保留）
4. API 异常堆栈表是否排到下一期
5. 清理任务放 auth-service（补 `@EnableScheduling`）还是已有定时任务的 order-service
