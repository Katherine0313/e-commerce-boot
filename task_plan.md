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
| 分布式中间件 | Redis **已接入**（分类树缓存）；Seata/MQ/分布式锁/分布式 ID 仍后置 |

## 微服务拆分方案

9 个服务（1 网关 + 7 业务 + 1 AI Agent），按 DDD 边界 + 数据归属划分：

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
| `ai-agent-service` | AI 导购 Agent：对话 + 意图路由 + RAG + MCP + Function Calling | 无（OpenFeign 调 product/order） | 9008 |

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
| AI 框架 | Spring AI 2.0.1（官方） | 不用 Spring AI Alibaba（Boot 4 兼容性）；LLM 走硅基流动 DeepSeek-V4-Flash |
| 数据库 | MySQL 8.0（Docker 容器 `mysql-dev`，开发期端口 3307） | 数据用命名卷 `mysql-dev-data` 持久化；本机 MySQL80 仍在 3306 可回退 |
| ORM | MyBatis + XML（现状，不引入 MP） | 保持已有约定 |
| Redis | 5.0.14（Windows 本机，`D:\Software\tools\redis\Redis-x64-5.0.14.1`） | 分类树缓存，StringRedisTemplate + Jackson 纯 JSON（无 @class） |

## 各阶段

### 阶段 0：基础设施 + 版本兼容验证（关键，先行）
- [x] Docker：装 Docker Desktop（用户级安装到 `AppData\Local\Programs\DockerDesktop`）；`docker-compose` 起 Nacos + MySQL（MySQL 走 Docker 容器，端口 3307 避开本机 MySQL80 的 3306，数据用命名卷 `mysql-dev-data` 持久化，数据已从本机 mysqldump 迁入）；8 个业务服务开发期用 IDE/mvn 跑，跑通后再逐个容器化
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
- [x] shops 表 + 商品域 4 表（shops/categories/tags/products）建表脚本 `docs/sql/product.sql` + 模板店种子数据（已迁入，见 findings「商品域数据库迁移踩坑点」）
- [ ] 开沙箱：克隆模板店数据（按 shopId=1 查 → 批量 insert 新 shopId）
- [ ] 过期回收：定时扫 shops 表，调各业务服务清数据
- [ ] `templateGuard` 等价逻辑（禁写模板店）
- **状态：** in_progress（shops 表已建、模板店数据已迁；沙箱克隆/回收/守卫未做）

### 阶段 3：product-service（最大域）
- [x] 商品浏览只读接口骨架：`GET /api/products`、`/api/products/{id}`、`/api/categories`、`/api/tags`（controller/service/impl/vo 分层 + 占位数据，不接库）；接入免登录白名单 `EXCLUDE_AUTH_PATHS`，gateway 补 product-service 路由
- [x] products / categories / tags 表已建 + 数据迁入（32 商品含下架、22 分类、1 标签）+ 图片转 webp 迁入 + 浏览接口接库（真实查询替换占位数据）
- [x] Redis 分类树缓存：`category:parent:{id}` 按一级父分类一棵树一个 key + `category:parent:ids` 索引，纯 JSON 序列化（见 findings「Redis 缓存（分类树）」）
- [ ] parameters / stock / business_onsale 表；商品 CRUD、分类树、标签绑定、参数组/参数项、库存调整、促销（单规格：价格/库存直接挂商品上，不做多规格 SKU）
- **状态：** in_progress（商品浏览已接库、数据+图片已迁；其余表与 CRUD 未做）

### 阶段 4：order-service
- [x] orders / order_items / cart / consignee_address 表 + 数据迁移（`docs/sql/order.sql` 建表 + `order-data.sql` 迁数据：52 订单 / 76 订单项 / 1 购物车 / 15 地址；consignee_address 表已随迁，但服务归属按约定仍归 user-service，接口未做）
- [x] 购物车 CRUD（CartController 5 接口：列表 / 加购 / 改数量 / 移除 / 清空；商品快照同库直接查 products 表，不信任前端价格）
- [ ] 购物车：下单成功后从购物车移除已下单商品（`OrderServiceImpl` 当前完全未引用 cart，下单不清购物车）
- [ ] 购物车：加购时校验库存 / 上架状态（现仅校验商品存在，`ProductMapper.findById` 无 `stock` / `listing_status` 过滤）
- [ ] 购物车端到端验证（5 接口未实测）+ 前端 uniapp 补 cart api 模块（`pages/cart/cart.vue` 未调接口）
- [x] 下单 + 支付/确认收款/发货/完成/取消（OrderStatusEnum 数字枚举 + 状态机 + 库存扣减/恢复 + order_status 字段迁移字符串→数字；**迁移脚本已于 2026-10-09 在 Docker 3307 执行**）
- [x] 待付款超时自动关闭（@Scheduled 每分钟扫，超 30 分钟恢复库存+改取消，不软删除保留订单记录，并发二次确认）
- [ ] 取消订单自动生成退款售后单（only_refund），关联 aftersale
- **状态：** in_progress（下单/状态流转/超时关闭/购物车 CRUD 已实现并编译通过；购物车有 3 项缺口见上，售后关联未做、未启动端到端验证）

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

### 阶段 8：ai-agent-service（AI 导购 Agent，小程序悬浮 AI）
- [x] 新建 ai-agent-service 骨架（Spring AI 2.0.1 + 硅基流动 DeepSeek + OpenFeign + MCP + SimpleVectorStore），端口 9008，注册 Nacos + 接入网关 + 免登录白名单
- [x] 对话接口（普通 + SSE 流式）+ 意图路由（Graph 工作流轻量版）+ 商品查询工具（@Tool 调 product-service）+ 下单工具预留（OrderTool）+ RAG（DocumentLoader/Embedding/RagRetriever）+ MCP（@McpTool）
- [x] 编译通过 + 启动验证（Nacos 注册、对话、Tool Calling、SSE 流式、MCP 工具注册均实测通过）
- [ ] 真实商品推荐端到端（product-service 启动后验证，当前返回 DeepSeek 幻觉商品）
- [ ] AI 下单闭环：依赖 order-service 下单接口（阶段 4），OrderTool.placeOrder 接 Feign + HITL
- [ ] RAG 生产化：SimpleVectorStore → Milvus/PgVector
- **状态：** in_progress（Agent 骨架 + 对话 + 工具调用已验证；真实推荐/下单待下游服务）

### 阶段 9：推荐服务（Python，小程序首页「猜你喜欢」）
- [x] Python FastAPI 服务骨架（`recommender-python/recommend/`，端口 9009）：`GET /recommend` 在线推荐 + `datasource` 抽象层（先读原 SQLite 的 orders/order_items，后续切 MySQL）—— 已跑通（本地 `uvicorn main:app --port 9009`，`/health` `/recommend` `/train` 实测 OK）
- [x] RFM 分群 + KMeans 聚类 + 群体热销推荐（同簇热销过滤已购）+ 销量热门兜底；冷启动未登录/无历史走热门（方向定案见 findings「推荐服务（Python）方案」）
- [x] product-service 桥接接口：`GET /api/products/recommend`（复用网关 `/api/products/**` 路由，零改动）+ RestClient 调 Python + 超时热门兜底 —— 已端到端跑通
- [ ] 小程序首页「猜你喜欢」区块（复用 `ProductVO` 商品卡片）
- [ ] orders/order_items 迁 MySQL（随阶段 4 order-service 落地）+ Python 数据源切 `mysql_source`
- **状态：** in_progress（Python 服务 + Java 桥接已跑通；剩余：前端 UI / 订单表迁 MySQL）

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
| 数据库迁移脚本一律带 `--default-character-set=utf8mb4` 导入 | Windows mysql 客户端默认 gbk，不带会中文乱码（双重编码，gbk 查询还会假还原） |
| 商品域加物理外键 | products.tag_id/category_id → tags/categories（ON DELETE SET NULL）、categories.parent_id → categories（ON DELETE CASCADE）；用户只用模板店不克隆沙箱，物理外键可行且可靠，级联由 DB 兜底 |
| 商品域表只留主键 + 外键自动索引 | 手动普通索引全去；外键列（tag_id/category_id/parent_id）索引是 InnoDB 建外键自动创建的，删不掉也不该删 |
| 分类缓存按一级父分类拆（`category:parent:{id}`），不用全量单 key | 一个父分类一棵树一个 key，更新按父 id 精准失效；商品不缓存（分类页懒加载按父类查商品） |
| Redis 值序列化用纯 JSON（StringRedisTemplate + Jackson），不用 @class | 用户要求禁止 @class；反序列化显式指定类型，避开 default typing 的安全与兼容坑 |
| 新表排序规则显式 `COLLATE utf8mb4_general_ci` | 服务器是 MySQL 5.7 不支持 8.0 默认的 0900_ai_ci，导出导入会失败；旧的 product/admin 表与已建表不管 |
| 订单域时间字段用 `created_at/updated_at`（_at 风格） | 忠于原 sqlite，orders 表内与 pay_at/confirm_at 等事件时间统一；与商品域 create_time/update_time 有意不同 |
| 订单域加物理外键（含 user_id → user.id） | 延续商品域「DB 兜底级联」；order_items.product_id 不加外键（订单项是下单快照，商品删了历史仍可读） |
| 购物车快照同库直接查 products 表 | 用户选「同库查表」：不信任前端价格，加购时后端查商品 name/image/price/description 落快照 |
| MySQL 走 Docker 容器（开发期 3307，volume 持久化） | 环境与生产一致、可一键起、可回退；端口 3307 避开本机 MySQL80，数据用命名卷 `mysql-dev-data` |
| Docker 数据挂载取舍：数据库用 volume，代码/配置用 bind mount | 数据库数据不该用 bind mount（性能差、Windows 文件锁）；以后容器化 Java 服务/Nginx 时，jar/配置/静态文件用 bind mount（`宿主机路径:容器路径`），改本机文件即生效 |
| 订单状态用数字 TINYINT + 枚举（OrderStatusEnum） | 电商标准做法：省存储、索引快、防拼写脏数据；状态机流转用枚举 canTransition 强校验 |
| 静态资源经网关按归属服务暴露（`/image/**` → product-service、`/upload/**` → user-service）；拼绝对 URL 以 `Host` 为准，由网关对 `/api/**` 路由配 `PreserveHostHeader` 保留客户端域名，统一走 `common/utils/UrlUtil` | 网关默认把 Host 改写成下游实例地址（`192.168.92.1:9006`），而 `X-Forwarded-Host` 又被它覆盖成同一个实例地址（5.0.3 源码取 `request.getURI().getHost()`），两个头都拿不到客户端域名 → 真机加载不到头像；静态资源不在 `/api/**` 内，天然免登录 |
| 凡是返回给前端的静态资源字段，一律由后端拼成完整 URL（`avatar` 在 `/api/wxlogin`、`/api/user/profile`、`/api/user/avatar` 三处口径一致） | 小程序把相对路径当包内资源（`Failed to load local image resource`）；前端约定「两个 avatar 字段都是完整 URL，前端不再拼域名」，口径必须一致 |
| 小程序 C 端免登录（启动静默 wx.login 换 openid），后台 B 端保持 admin 登录 | 用户拍板（2026-10-10）：C 端头像/手机号登录越做越复杂且需真机，直接免登录；购物车/下单靠静默登录 token 定位 userId，后台不受影响 |
| 增值保障由前端加购弹窗勾选、随 DTO 传入后端快照，不在商品表存固定保障费 | 增值保障是可多选（全面保修/一年意外保/电池无忧），价格按售价比例或固定、加购时才确定；商品表存单值覆盖不了多选场景 |
| 购物车数量上限用「实时 join products 取 stock + 后端校验」，不落 stock 快照 | stock 随下单/补货实时变化，快照会过期；join 实时取最准，后端 addItem/updateQuantity 权威校验兜底 |
| 购物车单品小计 subTotal 只读计算不落库 | subTotal = (price + insuranceFee) × quantity，是纯派生值；落库要同步维护 price/quantity/insuranceFee 三处变更，成本高且易脏 |
| 默认地址不单独接口，列表全量返回、前端取 is_default=1 | 地址数量少（≤几十），一次全量返回简单；单独 GET /address/default 多一次请求收益不大 |
| 默认地址唯一性由 Service 层事务保证（clearDefault 先清 0 + 落新默认）；删除默认自动把最新一条设为默认 | 「同一用户只有一个默认」前端保证不了，必须后端事务兜底；删默认后自动补位保证用户始终有默认，下单体验顺 |
| 省市区用微信原生 picker mode="region"，region 存「省 市 区」字符串 | 数据微信内置、零包体积（此前踩过 4MB 包上限）、自动级联；收货地址只是展示+发货，字符串够用，不拆 province/city/district、不建省市区表 |

## 遇到的错误

| 错误 | 尝试次数 | 解决方案 |
|------|---------|---------|
| （暂无） | — | — |
