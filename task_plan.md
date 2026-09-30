# 任务计划：Node 后端重构 + 微服务拆分 + 前端对接

将此文件作为任务的持久化路线图。在开始复杂工作前创建，并在阶段变化时及时更新。

## 目标

放弃 `e-commerce/server`（Node.js，Express + SQLite），把它的后台管理系统能力整合进本 Spring Boot 项目，让前端 `e-commerce`（Vue3 后台管理系统）对接，并拆分为微服务。

## 已确认决策

| 决策 | 结论 |
|------|------|
| 多租户店铺沙箱 | **保留**（Spring 端重做 shopId 隔离 + 沙箱回收 + 模板店克隆） |
| 微服务落地程度 | **真·多服务部署**（Nacos 注册 + Gateway 网关 + Feign 调用，独立部署） |
| 数据库 | SQLite → MySQL（表结构重设计，去掉号段 hack） |
| 前端对接 | 接口路径 `/api/**` 不变，前端 baseURL 指向网关；后台对应 `admin` 模块 |
| 分布式中间件 | **后置**（Seata/Redis/MQ/分布式锁/分布式 ID 本轮不上，微服务骨架跑通后按需补） |

## 微服务拆分方案

8 个服务（1 网关 + 7 业务），按 DDD 边界 + 数据归属划分：

| 服务 | 职责 | 数据/表 | 端口约定 |
|------|------|---------|---------|
| `gateway` | API 网关：路由、CORS、JWT 鉴权、`X-Shop-Id` 透传、限流 | 无 | 8080 |
| `auth-service` | 管理员登录/登出、JWT 签发（含 shopId/role）、RBAC | admin/role/permission（预留） | 9001 |
| `shop-service` | 多租户沙箱：开沙箱、克隆模板店、过期回收、模板店管理 | shops + 模板店数据源 | 9002 |
| `product-service` | 商品/分类/标签/参数/库存预警/促销 | products/categories/tags/parameters/stock/business_onsale + 关联表 | 9003 |
| `order-service` | 订单 + 购物车 + 待付款超时关闭 | orders/order_items/cart | 9004 |
| `aftersale-service` | 售后（仅退款/退货退款/换货） | after_sales/after_sale_items/after_sale_returns/after_sale_exchange_returns/after_sale_only_refunds | 9005 |
| `user-service` | 会员/收货地址，整合现有 `user` 模块（小程序） | users/consignee_address + 现有 user | 9006 |
| `file-service` | 图片上传/静态托管 | uploads + 上传目录 | 9007 |

**跨服务调用关系（Feign）：**
- `order-service` 下单 → `product-service` 查商品价格库存；支付/取消 → 回写库存（`product-service` 暴露库存内部接口）
- `aftersale-service` → `order-service` 查订单关联
- `shop-service` 回收沙箱 → 调各业务服务「按 shopId 清数据」的内部接口

**多租户在微服务下的落法：**
- 数据隔离：延续「共享库共享表 + `shopId` 字段」（MySQL 全局自增 id，查询一律带 `shopId` 过滤，去掉号段 hack）
- shopId 传递：网关鉴权后从 JWT 解析 `shopId`，注入 `X-Shop-Id` 请求头透传下游；各服务拦截器读头写 ThreadLocal
- 每张业务表都加 `shop_id` 列；MyBatis 层统一自动拼 `shop_id = ?` 条件，避免每个 SQL 手写

## 技术选型

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 4.1.0 | 现状 |
| Spring Cloud | 2025.1.x | 与 Boot 4.1 配套 |
| Spring Cloud Alibaba | 2025.1.0.0 | Nacos 客户端，已适配 Boot 4 |
| Nacos | server 3.1.1（稳定） | 注册 + 配置中心 |
| Gateway | Spring Cloud Gateway | 网关 |
| 服务调用 | OpenFeign | |
| 数据库 | MySQL（每服务独立库或共享库待定，见风险） | 现状 MySQL |
| ORM | MyBatis + XML（现状，不引入 MP） | 保持已有约定 |

## 各阶段

### 阶段 0：基础设施 + 版本兼容验证（关键，先行）
- [x] Docker：装 Docker Desktop（用户级安装到 `AppData\Local\Programs\DockerDesktop`）；`docker-compose` 只起 Nacos（MySQL 复用本机 MySQL80，3306 已占用、库 `e_commerce_db` 已建好）；8 个业务服务开发期用 IDE/mvn 跑，跑通后再逐个容器化
- [x] 改造为 Maven 多模块父工程（现 `e-commerce-boot` 变父工程，抽 `common` 模块下沉 `Result`/`BusinessException`/`GlobalExceptionHandler`/`JwtUtil`/`UserContext`/`JwtInterceptor` 等框架层）—— 已完成，10 模块 `mvn clean compile` 全绿
- [x] 引入 Spring Cloud 2025.1.3 + SCA 2025.1.0.0 + Nacos + Gateway，验证 Boot 4.1.0 兼容性（编译通过；Gateway 用 `spring-cloud-starter-gateway-server-webflux`，路由前缀 `spring.cloud.gateway.server.webflux.routes`，`lb://` 需配 loadbalancer）
- [x] 起最小服务 + 注册到 Nacos + Gateway 路由打通：auth-service、user-service 均注册成功（`nacos registry ... register finished`）；gateway 8080 经 `lb://user-service` 转发到 9006，返回一致（HTTP 401 + Result 结构），链路已验证
- [x] Nacos 3.1.1 与 Boot 4.1 兼容性验证通过，无需降级 Boot 或换注册中心（踩坑细节见 findings.md「Nacos 3.x 部署踩坑」）
- **状态：** completed（Nacos 已起、服务注册成功、网关路由打通）

### 阶段 1：gateway + auth-service
- [x] auth-service 登录接口代码：`POST /api/login`（admin 账号密码）、`GET /api/user/info`、`POST /api/logout`；`JwtUtil.generateAdminToken` claims 加 `id`/`username`/`role`/`shopId`
- [x] admin 表建表脚本 `docs/sql/admin.sql`（建表 + 种子 admin/admin123）—— 待执行（按规范不代跑 DB 初始化）
- [x] gateway 路由：`/api/login`、`/api/logout`、`/api/user/info` → `lb://auth-service`
- [ ] admin 表实际执行建表（`mysql -uroot -p123456 e_commerce_db < docs/sql/admin.sql`，幂等，建表 + 种子 admin/admin123）+ RBAC 权限表（role/permission 拆分）
- [ ] gateway：CORS、JWT 校验过滤器、`X-Shop-Id` 透传、限流
- [ ] 前端登录/登出/鉴权这条链路端到端跑通（需先执行建表脚本）
- **状态：** in_progress（登录接口已就绪并编译通过，待建表 + 端到端验证）

### 阶段 2：shop-service（多租户基础设施）
- [ ] 设计 MySQL 表结构（shops 表 + 17 张业务表的 shop_id 列），产出建表脚本
- [ ] 模板店种子数据（把原 SQLite 演示数据迁成 MySQL 种子 SQL）
- [ ] 开沙箱：克隆模板店数据（按 shopId=1 查 → 批量 insert 新 shopId）
- [ ] 过期回收：定时扫 shops 表，调各业务服务清数据
- [ ] `templateGuard` 等价逻辑（禁写模板店）
- **状态：** pending

### 阶段 3：product-service（最大域）
- [x] 商品浏览只读接口骨架：`GET /api/products`、`/api/products/{id}`、`/api/categories`、`/api/tags`（controller/service/impl/vo 分层 + 占位数据，不接库）；接入免登录白名单 `EXCLUDE_AUTH_PATHS`，gateway 补 product-service 路由
- [ ] products / categories / tags / parameters / stock / business_onsale + 关联表（表结构设计属阶段 2）
- [ ] 商品 CRUD、分类树、标签绑定、参数组/参数项、库存调整、促销（单规格：价格/库存直接挂商品上，不做多规格 SKU）；浏览接口的占位数据替换为真实查询
- **状态：** in_progress（浏览只读接口骨架已完成，表结构/CRUD 未做）

### 阶段 4：order-service
- [ ] orders / order_items / cart 表
- [ ] 下单（Feign 查 product）、支付/取消/发货/确认收货/完成、购物车 CRUD
- [ ] 待付款超时自动关闭（迁移原 30s 定时扫 → 微服务内定时任务）
- **状态：** pending

### 阶段 5：aftersale-service
- [ ] after_sales + 三类子表（仅退款/退货退款/换货）
- [ ] 售后单 CRUD、提交退货、状态流转；Feign 关联订单
- **状态：** pending

### 阶段 6：user-service + file-service
- [ ] user-service：users / consignee_address；整合现有 user 模块（小程序登录链路保留，骨架已迁入）
- [ ] file-service：图片上传（白名单校验复用现有逻辑）、静态托管、uploads 表
- **状态：** pending

### 阶段 7：前端对接联调
- [ ] 前端 baseURL 指向网关，登录响应结构对齐，端到端跑通商品/订单/售后
- [ ] 更新 `docs/openapi.yaml` 或按服务拆文档；确认 Result 结构与前端拦截器兼容
- **状态：** pending

## 风险与待确认

| # | 风险/待确认 | 影响 | 处置 |
|---|---|---|---|
| 1 | Nacos 对 Boot 4.1 的实际兼容性 | 阶段 0 首验，不通过全盘阻塞 | 阶段 0 先验证，备选降级 Boot 3.5.x |
| 2 | 每服务独立库 vs 共享库（多租户） | 影响事务边界与 Feign 调用复杂度 | 建议先「共享库共享表 + shopId」起步，后续按需拆库 |
| 3 | 沙箱回收的跨服务一致性 | shop-service 调多个服务清数据，非事务 | 用「软删除 + 最终一致」而非强事务，回收失败重试 |
| 4 | 前端登录返回结构差异（Node `{token,user}` vs Spring Result） | 前端解包字段 | 阶段 1 对齐，或 auth-service 兼容返回 |
| 5 | 现有 `user` 模块（小程序）与 `admin`（后台）账号体系并存 | 两套登录入口 | admin 走 auth-service，user 走 user-service，JWT type 区分 |
| 6 | Spring Cloud 组件在 Boot 4 下的学习/调试成本 | 单人项目进度 | 阶段 0 先搭最小可跑骨架再铺开 |

## 已做决策

| 决策 | 理由 |
|------|------|
| 保留多租户沙箱 | 用户确认；这是原系统的灵魂特性，前端大量接口依赖 shopId 隔离 |
| 真·多服务部署 | 用户确认；Nacos + Gateway + Feign |
| shopId 隔离沿用「共享表 + shop_id 列」，不沿用 id 号段 hack | 号段 hack 是 sql.js 单库妥协；MySQL 全局自增 + 查询过滤更干净 |
| 服务边界按 DDD（商品/订单/售后/会员/店铺/认证/文件）切 | 与前端 api 目录、Node 的 DB 文件划分天然对齐，迁移映射清晰 |
| 数据库 SQLite→MySQL 重设计表结构 | 原 sql.js 内存库不可直接复用，且要加 shop_id 列 |
| Docker 分阶段使用 | 开发期只用 Docker 跑 Nacos/MySQL 中间件，业务服务 IDE 跑；跑通后再容器化部署 |
| 分布式中间件后置 | 用户确认；先跑通微服务骨架，再按需引入 Seata/Redis/MQ/分布式锁/分布式 ID |

## 遇到的错误

| 错误 | 尝试次数 | 解决方案 |
|------|---------|---------|
| （暂无） | — | — |
