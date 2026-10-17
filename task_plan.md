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
| 分布式中间件 | Redis **已接入**（分类树缓存 + 下单幂等）；Seata/MQ/分布式锁/分布式 ID 仍后置 |

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
| `user-service` | 会员/小程序登录，整合现有 `user` 模块 | 现有 user（收货地址已放 order-service） | 9006 |
| `file-service` | 图片上传/静态托管 | uploads + 上传目录 | 9007 |
| `ai-agent-service` | AI 导购 Agent：对话 + 意图路由 + RAG + MCP + Function Calling | 无（OpenFeign 调 product/order） | 9008 |

**跨服务调用关系（Feign）：**
- `order-service` 下单 → `product-service` 查商品价格库存；支付/取消 → 回写库存（`product-service` 暴露库存内部接口）
- `aftersale-service` 查订单（归属校验 + 明细快照）：共享库直接读 `orders`/`order_items` 表（同 order-service 直接读 products），不跨服务 Feign
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
- [✓] Docker：装 Docker Desktop（用户级安装到 `AppData\Local\Programs\DockerDesktop`）
  - `docker-compose` 起 Nacos + MySQL（端口 3307 避开本机 MySQL80 的 3306，数据命名卷 `mysql-dev-data` 持久化，已从本机迁入）
  - 8 个业务服务开发期用 IDE/mvn 跑，跑通后再逐个容器化
- [✓] 改造为 Maven 多模块父工程：`e-commerce-boot` 变父工程，抽 `common` 下沉框架层
  - 下沉 `Result`/`BusinessException`/`GlobalExceptionHandler`/`JwtUtil`/`UserContext`/`JwtInterceptor`
  - 已完成，10 模块 `mvn clean compile` 全绿
- [✓] 引入 Spring Cloud 2025.1.3 + SCA 2025.1.0.0 + Nacos + Gateway，验证 Boot 4.1.0 兼容性
  - 编译通过；Gateway 用 `spring-cloud-starter-gateway-server-webflux`，路由前缀 `spring.cloud.gateway.server.webflux.routes`，`lb://` 需配 loadbalancer
- [✓] 起最小服务 + 注册到 Nacos + Gateway 路由打通
  - auth-service、user-service 均注册成功；gateway 8080 经 `lb://user-service` 转发到 9006，返回一致（HTTP 401 + Result 结构），链路已验证
- [✓] Nacos 3.1.1 与 Boot 4.1 兼容性验证通过，无需降级 Boot 或换注册中心（踩坑细节见 findings.md「Nacos 3.x 部署踩坑」）
- **状态：** completed（Nacos 已起、服务注册成功、网关路由打通）

### 阶段 1：gateway + auth-service
- [✓] auth-service 登录接口代码：`POST /api/login`（admin 账号密码）、`GET /api/user/info`、`POST /api/logout`
  - `JwtUtil.generateAdminToken` claims 加 `id`/`username`/`role`/`shopId`
- [✓] admin 表建表脚本 `docs/sql/admin.sql`（建表 + 种子 admin/admin123，含 `phone` 字段）
- [✓] gateway 路由：`/api/login`、`/api/logout`、`/api/user/info` → `lb://auth-service`
- [✓] admin 表已执行建表 + 种子 admin/admin123（2026-10-17 手动执行，含 `phone` 字段）
- [ ] RBAC 权限表（role/permission 拆分）
- [ ] gateway：CORS、JWT 校验过滤器、`X-Shop-Id` 透传、限流
- [ ] 前端登录/登出/鉴权这条链路端到端跑通（需先执行建表脚本）
- **状态：** in_progress（登录接口已就绪并编译通过，admin 表 + 种子账号已建；待端到端验证 + RBAC）

### 阶段 2：shop-service（多租户基础设施）
- [✓] shops 表 + 商品域 4 表（shops/categories/tags/products）建表脚本 `docs/sql/product.sql` + 模板店种子数据（已迁入，见 findings「商品域数据库迁移踩坑点」）
- [ ] 开沙箱：克隆模板店数据（按 shopId=1 查 → 批量 insert 新 shopId）
- [ ] 过期回收：定时扫 shops 表，调各业务服务清数据
- [ ] `templateGuard` 等价逻辑（禁写模板店）
- **状态：** in_progress（shops 表已建、模板店数据已迁；沙箱克隆/回收/守卫未做）

### 阶段 3：product-service（最大域）
- [✓] 商品浏览只读接口骨架：`GET /api/products`、`/api/products/{id}`、`/api/categories`、`/api/tags`（controller/service/impl/vo 分层 + 占位数据，不接库）；接入免登录白名单 `EXCLUDE_AUTH_PATHS`，gateway 补 product-service 路由
- [✓] products / categories / tags 表已建 + 数据迁入（32 商品含下架、22 分类、1 标签）
  - 图片转 webp 迁入 + 浏览接口接库（真实查询替换占位数据）
- [✓] Redis 分类树缓存：`category:parent:{id}` 按一级父分类一棵树一个 key + `category:parent:ids` 索引，纯 JSON 序列化（见 findings「Redis 缓存（分类树）」）
- [✓] 商品关键词搜索（`GET /api/products?keyword=` 匹配 name/sub_title/brand；前端 search 页 + 首页/分类页顶部搜索框跳转）
- [ ] parameters / stock / business_onsale 表；商品 CRUD、分类树、标签绑定、参数组/参数项、库存调整、促销（单规格：价格/库存直接挂商品上，不做多规格 SKU）
- **状态：** in_progress（商品浏览已接库、数据+图片已迁；其余表与 CRUD 未做）

### 阶段 4：order-service
- [✓] orders / order_items / cart / address 表 + 数据迁移（`order.sql` 建表 + `order-data.sql` 迁数据：52 订单 / 76 订单项 / 15 地址）
  - address 表原名 consignee_address，收货地址 CRUD 已实现在 order-service
- [✓] 购物车 CRUD（CartController 5 接口：列表 / 加购 / 改数量 / 移除 / 清空；商品快照同库直接查 products 表，不信任前端价格）
- [ ] 购物车：下单成功后从购物车移除已下单商品（后端 `OrderServiceImpl` 未引用 cart，现由前端下单成功后 `clearCart` 兜底）
- [✓] 购物车：加购时校验库存（addItem/updateQuantity 超库存抛 400，实时 join products 取 stock）
- [ ] 购物车：加购时校验上架状态（`ProductMapper.findById` 无 `listing_status` 过滤）
- [ ] 购物车端到端验证（5 接口未实测；前端 cart api 模块 + 购物车页已做）
- [✓] 下单 + 支付/确认收款/发货/完成/取消
  - OrderStatusEnum 数字枚举 + 状态机 + 库存扣减/恢复 + order_status 迁移字符串→数字（2026-10-09 在 Docker 3307 执行）
- [✓] 待付款超时自动关闭（@Scheduled 每分钟扫，超 30 分钟恢复库存+改取消，不软删除保留订单记录，并发二次确认）
- [✓] 下单幂等（idempotencyKey + Redis SETNX 占位 + 4 个 safe* 降级；防弱网重试/双击重复下单）
- [✓] 小程序前端下单链路接入：下单确认页 + 三条入口（立即购买/去结算/猜你喜欢）+ 幂等键前端用法
- [✓] 删除订单（软删 `is_deleted=1`，`DELETE /api/orders/{id}`，校验归属不限状态；前端「删除订单」按钮已接）
- [✓] 订单关键词搜索（`GET /api/orders?keyword=` 按订单号/收货人/商品名模糊匹配；前端列表页搜索框）
- [✓] 支付方式枚举 PayTypeEnum（payType 存英文 code，OrderVO 返回 payTypeDesc 中文；fromCode 忽略大小写兼容 wechat/WeChat；前端已统一传 WeChat、下单默认 WECHAT，2026-10-17 修 pay_type 存 NULL/小写）
- [✓] 订单状态双文案（OrderStatusEnum desc=顾客视角 / adminDesc=商家视角：待确认→顾客「待发货」、待发货→顾客「待发货」，2026-10-17 把「处理中」统一为「待发货」）
- [✓] 订单物流轨迹（假数据）：`CourierEnum` + `GET /api/orders/{id}/logistics`（参考旧 Node）+ `OrderVO` 附 logisticsStatus/latestTrace（courier 转中文），前端物流行/物流卡/物流详情页（2026-10-17）
- [✓] 订单时间时区修复收尾：`updateStatus`/`delete` 的 MySQL `NOW()` 全改 Java `LocalDateTime.now()` 传参，存量 created_at>pay_at 脏数据已修（2026-10-17）
- [ ] 取消订单自动生成退款售后单（only_refund），关联 aftersale
- **状态：** in_progress（下单/状态流转/超时关闭/购物车 CRUD/下单幂等/小程序前端下单链路已实现并编译通过，下单接口已评审加固（订单号防撞唯一索引 + 手机号 11 位校验）；购物车有 3 项缺口见上，售后关联未做、未启动端到端验证）

### 阶段 5：aftersale-service
- [✓] after_sales + 三类子表建表 + 数据迁移（`after-sale-data.sql`：28 售后单 / 43 明细 / 17 仅退款 / 4 换货）
  - 丢弃 11 条孤儿 only_refund，`after_sale_items` 字段名 ALTER 统一为 `after_sale_id`
- [✓] C 端售后接口（`AfterSaleController` 5 接口：申请/列表/详情/状态常量/提交退货）
  - 归属隔离通过 `order_id LEFT JOIN orders` 的 `user_id`，修正原 Node 越权；gateway 补 `/api/after-sales/**` 路由
- [ ] 后台审核/删除（`review`/`delete`，走 `/api/admin/after-sales/**`）
- [ ] 取消订单自动生成退款售后单（关联 order-service，见阶段 4）
- **状态：** in_progress（C 端售后接口已实现并编译通过；后台审核/删除未做、端到端未验证）

### 阶段 6：user-service + file-service
- [✓] user-service：整合现有 user 模块（小程序登录/资料/头像/昵称/注销链路已在 user-service 跑通）；收货地址已实现在 order-service（address 表），不归 user-service
- [ ] file-service：图片上传（白名单校验复用现有逻辑）、静态托管、uploads 表
- **状态：** pending

### 阶段 7：前端对接联调
- [✓] 前端 baseURL 指向网关，登录响应结构对齐（商品浏览/加购/下单链路已接入）
- [✓] 小程序下单链路：下单确认页 + 三条入口（立即购买/去结算/猜你喜欢）已通，支付用模拟微信支付弹窗（6 位密码框 + 数字键盘，未接真实微信支付）
- [✓] 订单列表页 6 Tab（全部/待付款/待发货/待收货/评论/售后）+ 搜索框 + 从「我的」页跳转定位 Tab；订单卡片抽成模块内组件 OrderCard（pages/order/，非全局）
- [✓] 待发货订单详情页（pending-ship.vue，支付方式/下单时间/地址/商品/金额）+ 商品搜索页（search.vue）+ 收货地址管理页（address.vue 列表 + address-edit.vue 编辑，设默认/删除）
- [ ] 端到端跑通商品/订单/售后全链路（确认收款/发货是商家操作，走后台）
- [✓] 接口文档改为 springdoc 自动生成（手写 `docs/openapi.yaml` 已删）；Result 结构与前端拦截器已对齐
- **状态：** in_progress（商品/购物车/下单/订单列表链路已通；售后 C 端接口已实现；订单/商品搜索、订单列表 6 Tab、待发货详情、地址管理页已做，端到端未验证）

### 阶段 8：ai-agent-service（AI 导购 Agent，小程序悬浮 AI）
- [✓] 新建 ai-agent-service 骨架（Spring AI 2.0.1 + 硅基流动 DeepSeek + OpenFeign + MCP + SimpleVectorStore），端口 9008，注册 Nacos + 接入网关 + 免登录白名单
- [✓] 对话接口（普通 + SSE 流式）+ 意图路由（Graph 工作流轻量版）+ 商品查询工具（@Tool 调 product-service）
  - 下单工具预留（OrderTool）+ RAG（DocumentLoader/Embedding/RagRetriever）+ MCP（@McpTool）
- [✓] 编译通过 + 启动验证（Nacos 注册、对话、Tool Calling、SSE 流式、MCP 工具注册均实测通过）
- [ ] 真实商品推荐端到端（product-service 已接库启动，待重新验证；早前 product-service 未启动时返回 DeepSeek 幻觉商品）
- [ ] AI 下单闭环：依赖 order-service 下单接口（阶段 4），OrderTool.placeOrder 接 Feign + HITL
- [ ] RAG 生产化：SimpleVectorStore → Milvus/PgVector
- **状态：** in_progress（Agent 骨架 + 对话 + 工具调用已验证；真实推荐/下单待下游服务）

### 阶段 9：推荐服务（Python，小程序首页「猜你喜欢」）
- [✓] Python FastAPI 服务骨架（`recommender-python/recommend/`，端口 9009）：`GET /recommend` 在线推荐 + `datasource` 抽象层（先读原 SQLite 的 orders/order_items，后续切 MySQL）—— 已跑通（本地 `uvicorn main:app --port 9009`，`/health` `/recommend` `/train` 实测 OK）
- [✓] RFM 分群 + KMeans 聚类 + 群体热销推荐（同簇热销过滤已购）+ 销量热门兜底；冷启动未登录/无历史走热门（方向定案见 findings「推荐服务（Python）方案」）
- [✓] product-service 桥接接口：`GET /api/products/recommend`（复用网关 `/api/products/**` 路由，零改动）+ RestClient 调 Python + 超时热门兜底 —— 已端到端跑通
- [✓] 小程序首页「猜你喜欢」区块（前端 `components/Guess.vue` 已接 `GET /api/products/recommend`，复用 `ProductVO` 商品卡片）
- [✓] orders/order_items 迁 MySQL（已随阶段 4 order-service 迁移）
- [ ] Python 数据源切 `mysql_source`（当前仍读原 SQLite）
- **状态：** in_progress（Python 服务 + Java 桥接已跑通；剩余：Python 数据源切 MySQL）

### 阶段 10：安全加固（DDoS 防护）

分两层：**应用/网关层自己落地**（代码内），**网络/基础设施层靠云厂商**（代码外，上线前补）。现状单机开发期，网关是唯一入口（8080），Redis 骨架已接好，可直接做网关限流。

**应用/网关层（自己落地，按性价比排序）：**
- [ ] 网关限流：`RequestRateLimiter`（Redis 令牌桶）+ 按 IP 限流的 `KeyResolver`（C 端免登录，无用户身份，只能按 IP + 设备指纹；与阶段 1「gateway 限流」同项，此处细化落地，需给 gateway 加 `redis-reactive` 依赖 + 配置类）
- [ ] 分接口差异化限流：`ai-agent-service`（AI 大模型调用，DDoS 放大成 token 成本）单独极低配额；免登录 C 端接口（商品浏览 `/api/products`、图片 `/image/**`、上传 `/upload/**`）单独收紧
- [ ] 兜底硬限制：请求体大小上限（`MaxRequestSizeFilter` / multipart 大小）、网关 httpclient `connect-timeout`/`response-timeout`、连接数/线程上限（宁可排队拒掉也不 OOM）
- [ ] IP 黑名单 + 滑动窗口计数（Redis + Lua，GlobalFilter 里按 IP 超阈值拉黑一段时间）

**网络/基础设施层（代码外，上线前补）：**
- [ ] 静态资源迁 CDN（商品图 `/image/**`、头像 `/upload/**`，减轻网关压力；与现有静态映射 `/image/**`→product-service、`/upload/**`→user-service 对应）
- [ ] 网关前套云厂商高防 IP / WAF（阿里云/腾讯云），真被大流量打时单机代码扛不住

- **状态：** pending（方案已定，待动手）

### 阶段 11：后台实时提醒（WebSocket，后置）

> **现在不做，将来做。** 触发时机：后台管理端开始嫌「要手动刷新才看到新订单/新售后」。
> 现状盘点：全项目 **0 处 WebSocket**（无依赖、无配置、无代码），网关 9 条路由全为 `lb://` HTTP。
> 关键结论：这是**纯新增、非改造** —— 现架构没堵死任何路（Redis 已接、网关是 WebFlux 原生支持 ws、`JwtUtil` 可复用）。
> 范围：**仅后台管理端**（新订单 / 新售后提醒）；C 端订单状态推送、客服聊天**不在本阶段**。

- [ ] 先定推送端点归属（见「风险与待确认」#7）
- [ ] common 抽 WS 握手鉴权拦截器：握手阶段解析 `?token=`（浏览器 `new WebSocket` 不能带自定义 header），复用 `JwtUtil.parseToken` + admin/user 类型分流，对齐 `JwtInterceptor` 的 401/403 语义
- [ ] 会话注册表：`ConcurrentHashMap<userId, Set<WebSocketSession>>`；userId 存 `session.getAttributes()`（`UserContext` 是 ThreadLocal，WS 里失效，必须换法）
- [ ] 网关加 `ws://` 路由：写在 `spring.cloud.gateway.server.webflux.routes` 下（与现有 HTTP 路由规则不同）
- [ ] Redis pub/sub 跨实例广播：每实例订阅 `ws:push`，只推本地在线 session —— 解 `lb://` 长连接握手后钉死单实例的问题
- [ ] 事件发布点：`order-service`（下单成功 / 支付成功 / 发货）、`aftersale-service`（新售后单）各发一条 `ws:push`
- [ ] 后台管理端：建连 + 心跳 ping/pong + 断线重连 + **重连后拉全量对账**（补漏掉的消息）
- **状态：** pending（仅规划，未动手；预备调研见 findings「WebSocket 实时提醒接入预研（2026-10-15）」）
  - 2026-10-15 曾搭过一版练习骨架（order-service + 定时群发时间）并演示跑通，**当天已全部回滚**，知识点留在 findings「练习验证与回收（2026-10-15）」

## 定时任务规划（Spring Task）

原后台（Node）里定时任务只有「关单 + 沙箱回收」两个，是**当时没空做**，不代表其余不需要——以下按「有无前置依赖」分两档，全部纳入规划。

### 档 1：有实体，可直接做（无额外建表/接第三方）
| 任务 | 归属服务 | 触发方式 | 状态 |
|------|---------|---------|------|
| 超时未支付订单自动取消（超 30min 恢复库存 + 改取消） | order-service | `fixedDelay = 60000` | ✅ 已实现 |
| 沙箱过期回收（扫 `shops.expires_at < NOW()`，调各业务服务清数据） | shop-service | `cron = "0 0 * * * *"` 每小时 | ⏳ 待做（阶段 2） |
| 订单超时自动确认收货（发货满 N 天自动完成 + 结算） | order-service | `cron` 每天 | ⏳ 待做（状态机已支持 `SHIPPED → FINISHED`） |
| 定时下架过期活动商品 | product-service | `cron` 每天 | ⏳ 待做（`business_onsale` 有 `start_time`/`end_time`，随阶段 3 迁过来补） |

### 档 2：需先建表 / 接第三方（后置，先落地实体）
| 任务 | 归属服务 | 前置依赖 |
|------|---------|---------|
| 每日统计报表（凌晨统计昨日销售额/访问量/销量入库） | 待定（order/product 或独立统计模块） | 建统计报表表 + admin 报表页 |
| 清理过期优惠券 | 待定 | 建 `coupon` 表（当前仅 `business_onsale.allow_coupon` 布尔字段，无独立券实体） |
| 清理过期临时文件 / 失效购物车 | file-service / order-service | file-service 未做；cart 表已有 |
| 会员到期提醒 + 营销消息推送 | user-service | 会员等级表 + 微信订阅消息模板授权 |
| 第三方物流状态同步 | order-service | 接物流 API（当前为假数据：`getLogistics` 用订单 id 生成假轨迹，`courier`+单号仍手动录入） |

> 注：库存预警/同步**不需要**定时任务——项目库存是实时 join `products` 查（task_plan 已决策），无冗余副本可同步。
>
> 单实例注意：`@Scheduled` 是单机单实例，将来「真·多服务部署」多实例时同一任务会被重复执行（重复回收沙箱/重复关单）；任务入口需包分布式锁占位（先 Redis SETNX 实现，拿到锁才跑）。

## 风险与待确认

| # | 风险/待确认 | 影响 | 处置 |
|---|---|---|---|
| 1 | ~~Nacos 对 Boot 4.1 的实际兼容性~~（已解决：阶段 0 实测通过） | 阶段 0 首验，不通过全盘阻塞 | 已验证兼容，无需降级 Boot |
| 2 | 每服务独立库 vs 共享库（多租户） | 影响事务边界与 Feign 调用复杂度 | 建议先「共享库共享表 + shopId」起步，后续按需拆库 |
| 3 | 沙箱回收的跨服务一致性 | shop-service 调多个服务清数据，非事务 | 用「软删除 + 最终一致」而非强事务，回收失败重试 |
| 4 | 前端登录返回结构差异（Node `{token,user,shop}` vs Spring `{token,user}`） | 前端登录后若用 `shop` 字段会拿不到 | 前端有独立 `GET /api/shops/current`，大概率不依赖登录响应的 shop；待前端联调确认 |
| 5 | 现有 `user` 模块（小程序）与 `admin`（后台）账号体系并存 | 两套登录入口 | admin 走 auth-service，user 走 user-service，JWT type 区分 |
| 6 | Spring Cloud 组件在 Boot 4 下的学习/调试成本 | 单人项目进度 | 阶段 0 先搭最小可跑骨架再铺开 |
| 7 | 阶段 11 实时提醒的**推送端点归属**：网关内做 / 新建独立推送服务 / 挂在 order-service | 决定连接数、部署形态、前端要连几条 WS | 倾向**新建独立推送服务**（网关保持无状态、前端只连一条；端口避开 9009 用 9010）；动手前先与用户确认 |

## 遇到的错误

| 错误 | 尝试次数 | 解决方案 |
|------|---------|---------|
| （暂无） | — | — |
