# 任务计划：Node 后端重构 + 微服务拆分 + 前端对接

将此文件作为任务的持久化路线图，阶段变化时更新。已完成很久的细节已清理，踩坑/决策见 `findings.md`。

## 目标

把 `e-commerce/server`（Node.js + Express + SQLite）的后台能力整合进本 Spring Boot 项目，前端 `e-commerce`（Vue3 后台）对接，并拆分为微服务。

## 已确认决策

| 决策 | 结论 |
|------|------|
| 多租户店铺沙箱 | **已下线（2026-10-29）**：退化为单店，删除 shop-service、shop_id、沙箱克隆/回收 |
| 微服务落地程度 | **真·多服务部署**（Nacos 注册 + Gateway 网关 + Feign 调用，独立部署） |
| 数据库 | SQLite → MySQL（表结构重设计，去掉号段 hack） |
| 前端对接 | 接口路径 `/api/**` 不变，前端 baseURL 指向网关；后台对应 `admin` 模块 |
| 分布式中间件 | Redis **已接入**（分类树缓存 + 下单幂等）；Seata/MQ/分布式锁/分布式 ID 仍后置 |
| 凭据管理 | 统一走环境变量（`application-dev.yml` 已删，清单见 `docs/env.md`） |

## 微服务拆分方案

| 服务 | 职责 | 数据/表 | 端口 |
|------|------|---------|------|
| `gateway` | 路由、CORS、静态资源与 Swagger 聚合；JWT 鉴权由下游 `JwtInterceptor` 承担 | 无 | 8080 |
| `auth-service` | 管理员登录/登出、JWT 签发、RBAC | admin/role/permission | 9001 |
| `product-service` | 商品/分类/标签/参数/库存/促销 | products/categories/tags/parameters/stock/business_onsale | 9003 |
| `order-service` | 订单 + 购物车 + 报表 | orders/order_items/cart/address | 9004 |
| `aftersale-service` | 售后（仅退款/退货退款/换货） | after_sales 及子表 | 9005 |
| `user-service` | 会员/小程序登录 | user（地址在 order-service） | 9006 |
| `file-service` | 图片上传/静态托管（暂由 product-service 承担上传） | uploads | 9007 |
| `ai-service`（Python） | 导购机器人：多轮对话 + 商品问答 | products/categories（只读）+ Redis | 8000 |

**跨服务调用（Feign）：**
- `order-service` 下单 → `product-service` 查商品价格库存；支付/取消 → 回写库存
- `aftersale-service` 查订单、`order-service` 查商品均走**共享库直接 JOIN**，不跨服务 Feign

**单店模式（2026-10-29 起）：** 共享库共享表，不再有 `shop_id`；JWT 只签用户/管理员身份，查询不带店铺过滤。

## 技术选型

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 4.1.0 | 现状 |
| Spring Cloud | 2025.1.x | 与 Boot 4.1 配套 |
| Spring Cloud Alibaba | 2025.1.0.0 | Nacos 客户端 |
| Nacos | 3.1.1 | 注册 + 配置中心 |
| Gateway | Spring Cloud Gateway | WebFlux |
| 服务调用 | OpenFeign | |
| AI 框架 | Python FastAPI + LangGraph | 独立服务 `ai-service`，LLM 走硅基流动 DeepSeek-V4-Flash |
| 数据库 | MySQL 8.0 / 本机 3306 | 远程 `193.112.176.228:3306/baota_ecommerce` 已连、勿动；本地每晚 22:00 备份 |
| ORM | MyBatis + XML | 保持已有约定 |
| Redis | 5.0.14（本机 Windows） | 分类树缓存 + 下单幂等，StringRedisTemplate + 纯 JSON |

## 各阶段

> 勾选规则：`[✓]` 已完成；`[ ]` 未完成；备注里写「待验证」表示代码/部署已完成，只差线上验证。

### 阶段 0：基础设施 + 版本兼容验证
- [✓] Maven 多模块父工程 + `common` 框架层（Result/异常/JwtUtil/UserContext/JwtInterceptor）
- [✓] Spring Cloud + SCA + Nacos + Gateway 搭建，Boot 4.1 兼容性验证通过，网关路由打通
- [✓] 全局 Jackson `LocalDateTime` 统一 `yyyy-MM-dd HH:mm:ss`（2026-10-23）
- **状态：** completed

### 阶段 1：gateway + auth-service
- [✓] admin 登录/登出/info 接口 + admin 表种子（admin/admin123）
- [✓] gateway CORS（WebFlux `CorsWebFilter` + 去重 `GlobalFilter`）
- [✓] 后台管理端登录端到端跑通
- [✓] RBAC P0（2026-10-30）：`admin_role/admin_menu/admin_user_role/admin_role_menu` + 4 角色 + JWT 权限 + `@RequirePerm` 拦截 + 前端菜单/按钮过滤；详见阶段 18
- [ ] 操作日志审计（AOP）：切面统一记录「谁 / 时间 / IP / 接口 / 修改前后数据」写入 `operation_log`，后台可检索；优先覆盖改价、库存调整、售后审核、删除等敏感操作（阶段 18 P1）—— **阶段 29 已落地基建**：`common` 的 AOP 基建 + `operation_log` 表 + 售后审核/删除打标（本地 + 生产建表已执行）；**剩下**：后台只读页面（菜单 + 权限码 `system:operation-log:list` + 列表页）、改价/库存调整打标
- [✓] JWT 鉴权由下游各服务 `JwtInterceptor` 从 token 解析（网关不做过滤器、不传 `X-Shop-Id`）
- [ ] 网关限流（并入阶段 10）
- **状态：** in_progress

### 阶段 2：shop-service（多租户基础设施）——已废弃
- [✓] 原沙箱能力（开箱/克隆/回收/visitorKey 复用）历史实现
- [✓] **2026-10-29 彻底下线**：删除 shop-service、网关路由、compose/release 项；JWT 去掉 shopId；18 张表删 `shop_id`；后台前端店铺名改静态配置
- **状态：** canceled（单店模式）

### 阶段 3：product-service（最大域）
- [✓] 商品/分类/标签/库存预警后台 CRUD + C 端浏览/搜索/收藏
- [✓] Redis 分类树缓存（按一级父分类一棵树一个 key）
- [✓] 图片上传：后台 `/api/admin/upload`、C 端 `/api/upload`（type 分流目录）
- [✓] 首页热门推荐 `GET /api/products/hot`（2026-10-29）：推荐分类优先 + 随机补足 + excludeIds
- [✓] 商品 `specs` 属性（有序 JSON 数组，非 SKU）+ 详情/列表/后台返回
- [✓] 分类重构 8 一级 / 21 二级（502 运动手表并入 501，新增 503 机械普通手表）；一级/二级都支持商品过滤
- [✓] 商品整理为 136 件（50 现有 + 76 去重种子 id 51~126 + 10 款机械/石英表 id 127~136）并已上生产
- [✓] 商品 title/specs 真实化（2026-10-30）：126 个非机械表重写标题与参数，去掉随机拼接；机械表修正 4 款、雷达改 R30939103；本地/生产已同步，详见 progress.md
- [✓] 106~136 商品实拍图 webp 化（2026-10-30）：90 张原图转主图/缩略图/详情图，合计 6.1MB；本地/生产已替换；128 缺图待补
- [✓] C 端分页 `GET /api/products/page`（分类/关键词/品牌/价格/排序），旧接口保持数组兼容
- [✓] `products.description` 重命名为 `title`（主标题），商品 title 改为 SEO 长标题；`sub_title` 保留
- [✓] 商品 `sub_title` 淘宝风格化：营销销量/加购 + 可开发票 + 品类口碑，一句话 17~24 字（2026-10-29）
- [ ] 前端接 specs / 分类侧边栏（**小程序已完成**：详情页渲染 specs、分类页/分类面板已接；**PC 待接**：详情 specs、首页分类/轮播仍是占位。分页已完成 2026-10-30：小程序分类/搜索、PC 列表/搜索均走 `/api/products/page`）
- [ ] parameters 表 + 参数组/参数项
- [✓] 促销模块（2026-10-30）：`promotion/promotion_item/promotion_rule/promotion_usage` + 后台 CRUD/提前结束/效果统计 + `common` 算价引擎（直减/满减叠加、互斥）+ 订单优惠快照与确认页报价；定时下架不需要（按时间窗实时判定）；DB 脚本已在本地/生产执行
- **状态：** in_progress

### 阶段 4：order-service
- [✓] orders/order_items/cart/address 迁移 + 购物车 CRUD + 下单/支付/发货/完成/取消状态机
- [✓] 待付款超时关闭、下单幂等（Redis）、删除订单、关键词搜索、物流轨迹（假数据）
- [✓] 后台订单管理 12 接口 + 前端订单页（分页/状态/关键词/日期筛选）
- [✓] 商品评价、取消订单自动生成退款售后单
- [✓] 报表：`/dashboard`、`/turnover`、`/turnover-report`、`/sales-ranking`（2026-10-24）
- [✓] 订单未支付一次性订阅消息（2026-10-25）：提交订单时授权 → 下单写入 `is_subscribe_unpaid` → 第 20 分钟推送 → 点击跳 `pending-pay?orderNo`；access_token 改为 Redis 全局共享
- [✓] 物流轨迹过滤未来时间（2026-10-26）：`buildLogistics` 只返回当前时间之前已发生的轨迹（`addTraceIfPast`）
- [✓] 工作台累计销售额/订单量改后端聚合（`/api/admin/report/headline`），新增本月运营数据报表 Excel 导出（`/api/admin/report/export`，2026-10-26，2026-10-27 由近 30 天改为本月）
- [ ] 运营数据导出验证（**代码/部署完成，待验证**）：order-service 与后台 `dist` 均已发布；待实际下载一次 xlsx
- [ ] 订阅消息上线（**代码/库/部署完成，待验证**）：生产库 `orders` 两列 + `idx_orders_unpaid_notify` 已确认存在，order/user 已随 TCR 部署；待小程序发行后验证推送
- [✓] ~~后台管理端 `dist` 重新构建上传（sandboxVisitor、订单金额标签）~~：沙箱已下线，后台 dist 已多次发布（2026-10-30）
- [ ] 购物车加购校验上架状态（`ProductMapper.findById` 无 `listing_status` 过滤）
- [ ] 购物车 5 接口端到端验证
- [✓] 地址/购物车归属校验补齐：`AddressMapper.findById/update/deleteById`、`CartMapper.update` 全部加 `user_id` 条件（2026-10-24，用户不再走沙箱方案）
- **状态：** in_progress

### 阶段 5：aftersale-service
- [✓] 三类售后建表迁移 + C 端申请/列表/详情/提交退货（按订单归属隔离）
- [✓] 后台审核/删除、单品售后、货物状态、两套退款原因、状态规则、详情物流
- [✓] 小程序售后页面（申请/详情/退货方式/回填物流）+ 售后列表 Tab/角标
- [✓] 换货子表 namespace 截断 + 退货方式未落库修复（2026-10-23）
- [ ] 退货入库单（仓库核验）
- [ ] 售后端到端验证
- **状态：** in_progress

### 阶段 6：user-service + file-service
- [✓] 小程序登录/资料/头像/昵称/注销
- [✓] 用户增长统计（支持 `period` 时间桶，2026-10-24）+ 意见反馈
- [ ] file-service：CDN/通用文件 + uploads 表（图片上传暂放 product-service）
- **状态：** in_progress

### 阶段 7：前端对接联调
- [✓] baseURL→网关、登录对齐、小程序下单/订单列表 6 Tab/详情/搜索/地址
- [✓] 后台工作台：四张统计卡、营业额面板、全局时间选择器、销量排名（2026-10-24）
- [✓] 工作台顶部累计销售额/订单量走后端聚合 + 「数据导出」按钮（本月运营报表，2026-10-26；2026-10-27 改为本月口径）
- [✓] 小程序分包 + 图片后端化（2026-10-24）：主包保留 4 个 tabBar 页 + 我的子页（12 页），商品/搜索/订单/售后拆 4 个分包；`OrderCard`/`AfterSaleCard` 移到 `components`；删除 15M 的 `static/image`，商品图/轮播/分享图全部走后端
- [✓] 生产部署（2026-10-24~26）：腾讯云 8G + Docker Compose（Nacos 3.1.1 + Redis 容器 + 8 个 Java 服务 + Python `ai-service`）+ 宝塔 MySQL；`api.jiemids.com` / `www.jiemids.com` 经 Cloudflare + Nginx；后台沙箱/图片/来单 WebSocket、小程序发行已通；部署文件见 `deploy/`
- [✓] AI 迁移上线（2026-10-26）：网关 `/api/agent/**` → `http://ai-service:8000`；线上 `/api/home` 200、`/api/cart/count` 401、`/api/agent/chat` 401（鉴权生效）
- [✓] 发布切 TCR 镜像（2026-10-28）：初始 9 个仓库，2026-10-29 移除 shop-service 后为 8 个（7 Java + ai-service）；compose 加 `image:`；`deploy/release.sh` 支持构建/推送/回滚；服务器已切 `image:` + `--no-build`
- [✓] 后台管理端一键发布（2026-10-28）：`deploy/release-web.sh` 构建 `../e-commerce` → rsync 到 `/www/wwwroot/jiemids.com`，保留运行时文件，支持 `--no-build` / `--dry-run` / `--rollback`
- [✓] PC 商城一键发布（2026-10-31）：新增 `deploy/release-mall.sh` 构建 `../ecommerce-mall` → rsync 到 `/www/wwwroot/xiaojiecpn.com`（路径写死，避免与后台端搞混；此前误用裸 `release-web.sh` 发错过一次）
- [ ] 分类图收尾（**仅剩清 EdgeOne 缓存**）：webp 已上传、生产库已改 `.webp`；product/user 已多次发布（含 2026-10-30 全量）
- [✓] 线上问题修复（2026-10-25）：小程序审核白屏（上传了 `dist/dev`，接口地址是 127.0.0.1 → 改传 `dist/build`）、分类接口 Redis 降级 + Redis 容器化、用户头像 http→https（`UrlUtil` 非本机强制 https）、机器人头像本地 webp→jpg（iOS 兼容）
- [ ] 微信二次提审：`dist/build` 包 + 完整录屏 + 后台三个域名（request/uploadFile/downloadFile）
- [ ] 微信审核：模拟支付、隐私政策/用户协议真实页面、AI/UGC 内容安全（VIP/优惠券/积分/协议死链已于 2026-10-28 用功能开关隐藏）
- [ ] 端到端跑通商品/订单/售后全链路（含商家确认收款/发货）
- **状态：** in_progress

### 阶段 8：导购机器人（Python ai-service）
- [✓] 小程序客服 AI 流式对话（2026-10-24）：`/api/agent/chat/stream` SSE 首字推送 + `sessionId` Redis 多轮历史（2h TTL）；前端 AI 流式优先、人工客服本地拦截、规则失败兜底
- [✓] 全局悬浮 AI 客服（2026-10-24）：`components/floating-chat` 右侧中间悬浮球 + 底部聊天面板，头像 `static/zongjie.jpg`（2026-10-25 由本地 webp 转 jpg，修 iOS 不显示）；Pinia `store/ai-chat` 共享会话，已注入 pages.json 注册的 29 个页面（专属客服页除外）
  - ⚠️ **约定：以后新增小程序页面，必须在模板 `<template>` 后手动加 `<FloatingChat />`，否则新页面没有悬浮球。**（uni-app 小程序没有 App 级全局组件，悬浮球靠逐页注入）
- [✓] 迁移到 Python（2026-10-26）：新增 FastAPI + LangGraph 服务 `recommender-python`，多轮对话 + 商品问答（直连 MySQL 只读）；网关 `/api/agent/**` 静态转发；前端契约不变
- [✓] 删除 Java `ai-agent-service` 与 Spring AI BOM（2026-10-26）
- [✓] 生产部署 `ai-service` + 网关切换（2026-10-26），旧 `ai-agent-service` 容器已移除
- [✓] 防滥用（2026-10-26）：对话接口强制 JWT（共用 `JWT_SECRET`）、每用户 10 分钟 20 次 / 每天 100 次、全局并发 10、消息 500 字、`max_tokens=600`、首字 12s / 总时长 55s 超时
- [ ] Function Calling：模型自动调用工具查库（`search_products` / `check_stock` / `compare_price`），不再只靠 prompt 里塞商品摘要
- [ ] 商品卡片渲染：后端返回结构化商品（id/名称/图/价/卖点），小程序/PC 渲染成可点击卡片（当前只渲染纯文本）
- [ ] RAG 知识库（可选）：售后政策、保修说明、尺码/兼容性问答检索
- [ ] AI 下单闭环（写操作需二次确认，当前不做）
- **状态：** in_progress

### 阶段 10：安全加固（DDoS 防护）

**应用/网关层（自己落地）：**
- [✓] AI 接口鉴权与限流（2026-10-26，Python 侧）：强制 JWT + 每用户 10 分钟 20 次 / 每天 100 次 + 全局并发 10 + 单次 token 上限
- [ ] 网关限流（`RequestRateLimiter` Redis 令牌桶 + 按 IP 的 `KeyResolver`）
- [ ] 分接口差异化限流：免登录浏览/上传收紧；用户维度下单频率（如 1 分钟 ≤5 次）、同一用户短时间重复提交直接拒绝
- [ ] 接口防刷联动：命中阈值后写临时黑名单（Redis，带 TTL），网关层统一拒绝（与 AI 侧限流口径对齐）
- [ ] 兜底硬限制（请求体大小、网关 connect/response timeout、连接数/线程上限）
- [ ] IP 黑名单 + 滑动窗口计数（Redis + Lua）

**网络/基础设施层（上线前补）：**
- [ ] 静态资源迁 CDN（`/image/**`、`/upload/**`）
- [ ] 网关前套高防 IP / WAF

- **状态：** pending

### 阶段 11：后台实时提醒（WebSocket）
- [✓] 单实例版：`order-service /ws/order` + 握手鉴权 + 单集合广播 + 支付成功推送 + 前端弹窗/语音/心跳/重连
- [✓] 线上连不上的修复（2026-11-01）：EdgeOne 回源剥掉 `Upgrade` 头 → nginx 写死 `proxy_set_header Upgrade websocket;`；`location ^~ /ws/` 从宝塔托管的主 conf 移到 `extension/jiemids.com/ws.conf`（防被重写冲掉），源站日志握手从 400 → 101
- [✓] RabbitMQ 最小 demo（2026-10-29，仅 dev）：`order-service` 加 `spring-boot-starter-amqp`，`demo.exchange/demo.queue` 启动自动发一条并消费打日志
- [ ] RabbitMQ fanout 跨实例广播（替代单机内存注册表，解 `lb://` 长连接钉死单实例）
- [ ] 事件补齐：发货、新售后单
- **状态：** in_progress

### 阶段 12：微信支付接入（真实支付）——已取消
> **2026-10-30 用户决定不接入真实支付**：继续使用 mock 支付（`payOrder` 直接推进订单状态），不实现 JSAPI 下单、回调验签/金额校验、主动查单、日终对账、`pay_record` 流水和支付凭据。
- **状态：** canceled（不接真实支付，保留 mock）

## 定时任务规划（Spring Task）

### 档 1：有实体，可直接做

| 任务 | 归属服务 | 触发方式 | 状态 |
|------|---------|---------|------|
| 超时未支付订单自动取消（30min 恢复库存 + 改取消） | order-service | `fixedDelay=60000` | ✅ 已实现 |
| 订单超时自动确认收货（发货满 7 天，`sign_status=auto`） | order-service | `fixedDelay=60000` | ✅ 已实现 |
| 订单未支付提醒（失效前 10 分钟，一次性订阅消息） | order-service | `fixedDelay=60000` | ✅ 已实现（待上线）；后续计划改 RabbitMQ 延迟队列 |
| ~~沙箱过期回收~~ | ~~shop-service~~ | — | ❌ 已随多租户下线（2026-10-29） |
| ~~定时下架过期活动商品~~ | — | — | ❌ 不需要：活动按时间窗实时判定，到期自动失效 |

### 档 2：需先建表 / 接第三方

| 任务 | 归属服务 | 前置依赖 |
|------|---------|---------|
| 每日统计报表入库 | 待定 | 统计报表表 + admin 报表页 |
| ~~清理过期优惠券~~ | — | — | ❌ 不需要：用户券过期按 `end_time` 读时判定 |
| 清理过期临时文件 / 失效购物车 | file-service / order-service | file-service 未做 |
| 会员到期提醒 + 营销消息推送 | user-service | 会员等级表 + 订阅消息模板 |
| 第三方物流状态同步 | order-service | 接物流 API（当前假数据） |
| ~~微信支付主动查单/对账~~ | — | ❌ 不接真实支付（2026-10-30） |

> 库存预警不需要定时任务（实时 join `products`）。
> `@Scheduled` 单机执行，将来多实例需加分布式锁（Redis SETNX 占位）。

### 阶段 13：RabbitMQ 接入（2026-10-29）
- [✓] 本地 Docker `rabbitmq:3.13-management`（5672/15672）；管理台 http://127.0.0.1:15672 ，账号 guest / guest
- [✓] order-service 加 `spring-boot-starter-amqp` + `spring.rabbitmq.*` 配置
- [✓] dev profile 最小 demo：`demo.exchange`/`demo.queue`，启动自动发送并消费
- [ ] 未支付 30 分钟延迟队列（TTL + 死信，替代 `sweepExpiredPendingPayOrders` 轮询）
- [ ] RabbitMQ fanout 跨实例广播来单提醒（替代单机内存注册表）
- [ ] 生产 compose 增加 rabbitmq 服务 + 凭据
- **状态：** in_progress（仅 demo）

### 阶段 14：PC 商城 Web 端（ecommerce-mall，2026-10-29）
- [✓] 新建 Vue 3 + Vite SPA，替换原静态企业站，上线 xiaojiecpn.com
- [✓] Nginx 同源代理 `/api`、`/image`、`/upload` + SPA 回退
- [✓] P0：布局、路由、请求层、首页/列表/详情/搜索接真实数据
- [ ] P1：轮播、分类导航、热门推荐、骨架屏/空态
- [ ] P2：C 端登录/注册（**用户名/手机号+密码、手机号+模拟验证码**，2026-10-31 支持双账号）+ 购物车 + 地址 + 结算/模拟支付（代码已完成，`docs/sql/user-pc-auth.sql` 已在本地与生产执行；待发版 user-service + 商城前端后联调）
- [ ] P3：下单/模拟支付/订单中心
- [ ] P4：收藏、评价列表、售后、AI 客服
- **状态：** in_progress（首版已上线，交易链路未接）

### 阶段 15：消息通知中心（站内信 + 订阅消息 + 短信）

> 现状：微信一次性订阅消息已用于「订单未支付提醒」（见阶段 4）；本阶段把通知统一收口，避免每个业务各自拼模板。
- [ ] 通知模板表 + 发送记录表（模板 key、业务场景、渠道、参数 JSON、发送状态、失败原因）
- [ ] 订阅消息扩展：发货通知、退款到账、售后状态变更（检测中 / 待寄回 / 换货完成）
- [ ] 站内信：`message` 表 + C 端列表/未读角标 + 后台按用户/全量发送；小程序「我的消息」入口
- [ ] 短信：接阿里云/腾讯云（通知类；后台手机号登录已于 2026-10-30 移除）
- [ ] 发送解耦：业务只发事件，通知中心统一发送（先 Spring 事件 + 线程池，量大再上 RabbitMQ）
- [ ] 失败重试 + 发送日志可查；与定时任务「会员到期提醒 / 营销推送」共用模板
- **状态：** pending

### 阶段 16：会员 / 积分体系（会员 v1 已完成，积分待做）

> 后台「经营工具 → 会员」已上线。优惠券的等级门槛已接入；积分、生日券仍未做；付费 VIP 因不接真实支付已取消。
- [✓] 会员等级 `member_level`（4 档 0/500/2000/5000）+ `user` 会员字段 + 成长值重算（2026-10-30）
- [✓] C 端会员中心 + 后台会员列表/等级配置 + 优惠券最低会员等级门槛（2026-10-30）
- [ ] 积分账户 + 积分流水（购物返积分、评价/晒单奖励、退款扣回）
- [ ] 生日券 / 每月等级券自动发放（复用优惠券定向发放）
- [ ] 积分抵现 / 积分兑券（订单级，排在券之后）
- ~~付费 VIP（依赖微信支付）~~ ❌ 不做：不接真实支付（2026-10-30）
- **状态：** in_progress（等级 v1 完成）

### 阶段 17：秒杀系统（规划，未开始）

> 拼团不做；秒杀与现有促销模块共用「活动/标签/倒计时」的展示层，但库存与并发模型独立，不塞进 `PromotionCalculator`。
- [ ] 秒杀场次表 + 秒杀商品表（场次开始/结束、秒杀价、限购、库存独立）
- [ ] Redis 预扣库存（Lua 原子扣减）+ MySQL 异步落单/回补，防超卖
- [ ] 下单限流/防刷（用户/IP 维度）+ 排队失败兜底
- [ ] 后台场次管理 + C 端秒杀专区与倒计时
- **状态：** pending

### 阶段 18：后台 RBAC 权限与审计（2026-10-30 起）

> P0/P1 代码已完成并全量发布，本地/生产库已建表。
> P1 把种子数据变成可视化维护，并补审计；2026-10-30 已补菜单管理与「权限变更后旧 token 立即失效」（Redis 失效时间点），不再需要等重新登录。

**P0 已完成**
- [✓] 表：`admin_role` / `admin_menu` / `admin_user_role` / `admin_role_menu`；脚本 `docs/sql/rbac/01-rbac-schema.sql`
- [✓] 4 角色：店铺管理员（admin 超管）/ 运营 / 客服 / 仓储发货
- [✓] 权限码 `模块:实体:动作`，菜单 M/C/F 三类；登录返回角色/权限/菜单，并写入 JWT
- [✓] `common @RequirePerm` + `PermissionInterceptor`，各服务 `/api/admin/**` 读写权限注解
- [✓] 前端菜单按权限过滤、路由守卫、`v-permission` 按钮指令；订单发货/售后审核等敏感按钮已接入
- [✓] 本地实测 operator 200、warehouse/service 403、C 端免登录 200

**P1：权限管理闭环**
- [✓] 「系统管理 → 管理员账号」：列表、新增、禁用、重置密码、分配角色（2026-10-30）
- [✓] 「系统管理 → 角色管理」：角色 CRUD + 菜单树勾选分配（2026-10-30）
- [✓] 密码 BCrypt：新增/重置用 BCrypt，历史明文登录成功后自动升级（2026-10-30）
- [✓] 「系统管理 → 菜单管理」：目录/菜单/按钮维护 + 树表默认收起/图标渲染/操作列对齐（2026-10-30，已发布）
- [ ] 动态路由：前端根据 `admin_menu` 生成路由和侧边栏（**暂缓**：静态路由 + 权限过滤已够用，动态路由收益低、回归风险高）
- [ ] 删除生产演示账号、去掉登录页演示预填
- [ ] 操作日志 + 登录日志：表 + AOP 注解 + 异步写入 + 后台只读页面
- [✓] 角色/菜单变更后强制下线或刷新 token：Redis `auth:admin:invalid-before:{id}` + JwtInterceptor 校验（2026-10-30，已发布）
- [✓] 管理员姓名语义修正：`admin.name` 由「角色名」改为真实姓名（2026-11-01，本地+生产均已执行；脚本 `docs/sql/rbac/03-fix-admin-name.sql`）
- [✓] 主键号段压平：沙箱段 8 位 id（>=100000）改回连续小号，级联改 17 个引用列 + 归位 AUTO_INCREMENT（2026-11-01，本地+生产均已执行；脚本 `docs/sql/normalize-sandbox-ids.sql`）
- [✓] 商品价格校正：按品类真实价重定 98 个商品售价（划线价 ×1.10、成本价 ×0.72），并统一全表口径（2026-11-01，本地+生产均已执行；脚本 `docs/sql/fix-product-pricing.sql`，只读预览 `fix-product-pricing-preview.sql`）
- [✓] 本地↔生产库一致性审计与补同步（2026-11-01）：字段 329/329、索引补齐（生产加 `user.uk_user_username`）、运营配置以生产为准反向对齐本地（recommend / tags / 标签绑定 / 客服2 账号）；核对工具 `docs/sql/check-local-prod-parity.sql`，同步脚本 `sync-local-to-prod.sql` / `sync-prod-to-local.sql`
- [ ] EdgeOne 给 `www.jiemids.com` 开 WebSocket 加速（用户侧控制台操作；当前靠 nginx 写死 `Upgrade websocket` 兜底）

**P2：增强**
- [ ] 数据权限（部门/门店维度，按需）
- [ ] 改价 / 退款审核 / 导出等敏感操作二次授权
- [ ] MFA、IP 白名单、登录设备管理
- [ ] 审计日志归档/导出、保留策略
- **状态：** in_progress（P0 完成；P1 账号/角色/菜单管理、BCrypt、强制下线已完成并发布，剩日志审计与演示账号清理）

### 阶段 19：增值保障规则收口（2026-10-31）

> 起因：C 端三处前端各自硬编码「增值保障」卡片，鼠标、手机壳这类配件也弹保障。
> 口径：**配件类不出 + 售价 < 300 不出**（无电池类目额外不出「电池无忧」），规则由后端统一出、前端只渲染。

- [✓] 规则下沉到 `common/insurance/InsurancePolicy`（单一口径，product-service 与 order-service 共用）
- [✓] `GET /api/products/{id}` 返回 `insuranceOptions: [{code,name,fee,desc}]`，按**最终展示价（含活动价）**计价；后台 `AdminProductVO` 同字段
- [✓] 小程序 `pages/goods/detail.vue`、PC 商城 `src/views/products/ProductDetail.vue` 改为按接口渲染，接口返空则整块隐藏
- [✓] 保障明细统一为 `{code,name,fee}`（PC 商城原为 `{name,price}`）
- [✓] order-service 一并收紧：加购/下单**不采信前端**传来的 `insuranceDetail`/`insuranceFee`，由 `support/InsuranceReviewer` 按同一规则复核重算
- [✓] order-service `entity/Product` 补 `categoryId`（`ProductMapper.xml` 补 `category_id`），加购用 `promotionPricingService.quoteDirect` 取活动价计价
- [✓] 本地验证：136 个上架商品逐个核对（81 个 3 项 / 29 个 2 项 / 24 个配件排除 / 2 个低价排除）；伪造加购与伪造下单（传 fee=999 + 假 code）均被剔除重算
- [ ] **发布生产**：后端 `release.sh product-service order-service` → PC 商城 `./deploy/release-mall.sh` → 小程序 `dist/build/mp-weixin`（后端必须先发）
- [ ] 后台商品预览 `e-commerce/src/pages/goods/list/Mall.vue` 仍是旧硬编码保障卡（用户 2026-10-31 决定先只管 C 端）
- [ ] 购物车里**加购时**用旧规则写入的历史快照不回溯重算

### 阶段 20：线上可用性巡检与站点配置治理（2026-10-31）

> 起因：`www.xiaojiecpn.com/` 首页 404、后台 `www.jiemids.com/goods/list` 404，均为 nginx 站点配置问题，与业务代码无关。

- [✓] 商城首页 404：EdgeOne 回源走 HTTPS(443)，但源站没有 xiaojiecpn.com 的 443 站点块 → 落到 default 站点返回 nginx 404 页（状态码却是 200）。已在 `html_xiaojiecpn.com.conf` 末尾追加 443 server 块（复用现成证书 + `extension/xiaojiecpn.com/*.conf`），`nginx -t` 通过后 reload
- [✓] 后台子路由 404：Vue3 history 路由但缺 `location /` 回落 → 新增 `extension/jiemids.com/spa.conf` 的 `try_files $uri $uri/ /index.html`
- [✓] `deploy/healthcheck.sh`：商城首页/商城接口/静态资源/后台/小程序两个接口；**商城首页故意不做 cache-buster**（按用户视角检查，才能发现"源站好、线上坏"）；连续失败 2 次才告警
- [✓] 巡检已上线：生产 `/www/java/healthcheck/healthcheck.sh` + cron 每 5 分钟，日志 `/www/logs/healthcheck.log`
- [ ] 宝塔打开 `xiaojiecpn.com` 的 **SSL 开关**（否则宝塔保存站点配置会重写主 conf、再冲掉 443 块 → 首页再次 404）
- [ ] 证书续签：`xiaojiecpn.com` 证书 2026-11-29 到期，设自动续签或换证
- [ ] 配告警 webhook（企业微信/钉钉机器人），写进 `/www/java/healthcheck/healthcheck.env` 的 `HEALTHCHECK_WEBHOOK`
- [ ] 检查 `api.jiemids.com` / `www.jiemids.com` 是否有同类缺 443 块 / 缺 SPA 回落的隐患（当前两个站点的 443 块都在）

### 阶段 22：促销活动可编辑 + 划线价去重（2026-11-01）

> 起因：线上「限时立减」活动看不到编辑按钮；C 端出现两个划线价（活动前原价 + 市场价）。

- [✓] 编辑权限放开：后端 `assertEditable` 允许「未开始 + 进行中」，已结束/已失效仍拦；前端 `PromotionList.vue` 按钮条件同步放开
- [✓] 编辑进行中的活动时弹窗提示生效范围（只影响新订单）
- [✓] 划线价去重放在后端一处：`PromotionServiceImpl.applyDisplay` 命中活动时 `marketPrice` 置 null，划线价只留「活动前原价」；客户端模板已按 `marketPrice` 判断，**不用重新编译**
- [✓] 本地实测（临时实例 9012 + 合成活动，已清理）：活动商品 price/originalPrice/marketPrice 符合预期；非活动商品不受影响；编辑进行中的活动 200 且新价立即生效
- [ ] **发布生产**：`release.sh product-service` → `release-web.sh`（后台前端）
- [✓] 删除死路打通（用户 2026-11-01 确认）：删除规则从「只有未开始能删」改成「**没有产生过订单就能删，有订单只能提前结束**」。后端新增 `countUsageByPromotionId`（`promotion_usage` 去重 order_id），有订单直接 400「该活动已产生订单，只能提前结束，不能删除」；`AdminPromotionVO` 加 `orderCount` 给前端判断；前端删除按钮在有订单时置灰并给出原因 tooltip。本地实测：有订单 → 400；无订单（进行中）→ 200 且删完列表不再显示、详情 404

### 阶段 21：后台账号软删除 + 「禁用」改「停用」（2026-11-01）

> 起因：账号删除按钮缺失，无法下线离职/误建的账号；删除若走物理 DELETE 会丢历史操作痕迹，故改为软删。
> 口径：`admin` 表加 `is_deleted`（0 正常 / 1 已删除），删账号 = 置 1 + `status=0` + 解绑角色。

- [✓] `admin` 表加 `is_deleted tinyint NOT NULL DEFAULT 0`（脚本 `docs/sql/add-admin-soft-delete.sql`，幂等：**本地与生产各跑一次**，生产执行前已备份 `/www/backup/e_commerce_db_2026-11-01_1504.sql`）
- [✓] Mapper 四处过滤：`findByUsername` / `findById` / `listAdmins` / `countAdmins` 全部加 `is_deleted = 0`；新增 `softDelete(id, now)`
- [✓] Service `delete(id)`：禁删 id=1（超管）、禁删当前登录账号；先解绑 `admin_user_role` 再软删；提交后写 Redis 令牌失效标记（旧 token 立刻 401）
- [✓] Controller `DELETE /api/admin/system/users/{id}`，权限码 `system:user:remove`（`admin_menu` id=812 早已存在，此前无人使用）
- [✓] 用户名唯一性改查全量（含已删）：已删账号占用时提示「用户名已被删除的账号占用，请换一个」，不误报「用户名已存在」
- [✓] 前端 `api/system.js` 加 `deleteAdminUser`；`UserList.vue` 加删除按钮（`v-permission="'system:user:remove'"`，popconfirm 二次确认）
- [✓] 文案统一「禁用」→「停用」（前端状态标签/按钮/弹窗/表单开关 + 后端提示语与注释）
- [✓] 本地验证（临时实例 9011）：列表/新建/删除/列表不再显示/旧 token 401/同名再建 400/删超管 400/删不存在 404 全过；测试账号已清理
- [ ] **发布生产**：auth-service `./deploy/release.sh auth-service` → 后台前端 `./deploy/release-web.sh`（**必须先发 auth-service**，否则前端会调一个尚不存在的接口）
- [ ] `admin` 表 `is_deleted` 与生产已一致（5 个账号全部 `is_deleted=0`），无需再跑数据修复

### 阶段 23：小程序首页分类面板 8 个分类（2026-11-01）

> 起因：首页只展示 6 个分类，分类面板本身是 4 列 × 2 行 = 8 格，第 7、8 个被 limit 挡掉了。

- [✓] `pages/index/index.vue` 的 `<CategoryPanel>` `:limit` 6 → 8
- [✓] `pages/index/component/category-panel.vue` 注释同步（4 列 × 2 行 = 8、骨架占 8 格；骨架循环本来就是 `v-for="n in 8"`）
- [ ] **发布**：重新构建并上传 `dist/build/mp-weixin`（小程序需重新提审版本）

### 阶段 24：促销详情弹窗独立组件 + 活动商品带图（2026-11-01）

> 起因：详情弹窗原来内联在列表页里，商品只显示名称和价格，没有主图。

- [✓] 后端：`PromotionItemVO` 加 `productName` / `image` / `salesPrice`；`PromotionMapper.listItemsWithProduct` 关联 `products` 带出 `image`、`sales_price`
- [✓] 前端：详情弹窗独立成 `e-commerce/src/pages/business/promotion/PromotionDetailModal.vue`（活动信息 descriptions + 活动商品表格带缩略图 + 满减阶梯表格），新建/编辑弹窗独立成 `PromotionEditModal.vue`（表单 + 提前结束/删除底部按钮），两个弹窗都用 `v-model:visible` + `activity-id` 由列表页驱动，`PromotionList.vue` 只剩列表逻辑
- [✓] 列表操作列精简为「详情 / 编辑」两个按钮；「提前结束」「删除」移到编辑弹窗底部左侧（有订单时删除置灰 + tooltip 说明原因）；已结束/已失效的活动打开弹窗走只读模式（表单禁用、隐藏保存），仍能删除
- [✓] 本地实测（临时实例 9012 + 合成活动 9001，已清理）：详情接口返回商品图片 `/image/upload/1.webp`、名称、活动价；生产活动 #1 的 10 个商品都有 `products.image`
- [ ] **发布生产**：`release.sh product-service` → `release-web.sh`（后台前端）

### 阶段 25：经营工具 Banner 管理 + 文章管理（2026-11-01，代码已完成，待发版）

> 需求来源：AI 生成的芋道风格文档（仓库根目录 `promotion`），已按本项目约定 + 用户 4 个决定改写为可执行方案。
> 用户 4 个决定：① 文章分类不单独做菜单 ② PC 商城需要文章详情页 ③ `recommend_hot` + `recommend_banner` 保留 ④ Banner 简化为单图（不再双图字段）。
> 后续追加：⑤ 新菜单授权给运营角色（role_id=2），与既有「经营工具」菜单归属一致。

- [✓] 方案改写完成，落到仓库根目录 `promotion`（含表结构、接口清单、后台表单交互、两端链路、改动文件清单、发布顺序、与原 AI 文档的 12 处差异说明）
- [✓] 用户确认（「可以动手了」）
- [✓] 建表 SQL `docs/sql/promotion/02-banner-article-schema.sql`（3 表 + `admin_menu` 503/504 + 按钮 540~542、550~552 + `admin_role_menu` 授权运营），**本地与生产同一份均已执行**（生产先备份 `/www/backup/e_commerce_db_2026-11-01_2048.sql`，SQL 经 stdin 管道导入，不在生产机写文件）
- [✓] product-service：实体/Mapper(+XML)/Service/Controller 全部落地 —— `Banner`、`ArticleCategory`、`Article`；后台 `/api/admin/banners`、`/api/admin/articles`、`/api/admin/article-categories`，C 端 `/api/banners`、`/api/articles`（含 `/by-title`、`/{id}/browse`）；常量 `BannerConstants`
- [✓] 网关路由（`gateway/application.yml` product-service Path 列表补 Banner/文章路径）+ 免登录白名单（`WebMvcConfig.EXCLUDE_AUTH_PATHS` 补 `/api/banners/**`、`/api/articles/**`）
- [✓] 上传接口按 type 分支鉴权：`banner`→`business:banner:add`、`article`→`business:article:add`、`body`→商品编辑或文章编辑任一权限
- [✓] 后台前端：`pages/business/banner/BannerList.vue`、`pages/business/article/ArticleList.vue`、`pages/business/article/ArticleCategoryModal.vue` + `api/banner.js`/`api/article.js` + 路由 `/business/banner`、`/business/article`
- [✓] PC 商城（`ecommerce-mall`）：`Home.vue` 轮播改接口驱动（无数据回落静态图）、新增 `views/article/ArticleDetail.vue` + 路由 `article/:id`、页脚「关于我们 / 隐私政策」按标题进文章
- [✓] 小程序（`e-commerce-uniapp`）：首页与分类页轮播改接口驱动、`components/custom-swiper.vue` 支持对象型 item、新增 `pages/article/detail.vue`（富文本）、`utils/features.js` 打开 agreement/privacy、关于我们页协议入口进文章详情
- [✓] 本地全量实测（临时实例 9013，合成数据已清理，三表归零）：后台 CRUD / 校验分支 / C 端只读 / 浏览量计数 / 标题重复提示 / 删除保护全过；**修掉 3 个缺陷**：C 端 Banner 缺 `statusDesc`、分类详情缺 `articleCount`、`type=body` 上传误要求商品权限
- [✓] 三端构建通过：后台 `npm run build`、PC 商城 `npm run build`（小程序无 npm 构建脚本，靠 diff 复核）
- [ ] **发布生产**：`release.sh product-service` → `release.sh gateway` → `release-web.sh`（后台前端）→ `release-mall.sh`（PC 商城）→ 小程序 HBuilderX 重新构建上传
- [ ] **后台录入素材**：建「关于我们 / 隐私政策 / 用户协议」三篇文章 + 上传 Banner 图到 `/image/banner/`，否则商城页脚与小程序协议入口跳过去是空文章

### 阶段 26：优惠券发放后用户端可见性（2026-11-01，代码完成，待发版）

> 起因：运营反馈「优惠券发放之后，用户要先去领券中心才能看到券 / 我的页优惠券数量还是 0」。
> 实测（本地 order-service 临时实例 9014/9015 + 合成券，已清理）结论：后台发券 → `user_coupon` → C 端 `GET /api/coupons/mine` 这条链路**本来是通的**，发到真实用户的券能看到、我的页数量也会变；真正的缺口是下面两个。

- [✓] 缺口一：后端会返回 `NOT_STARTED`（未生效）/ `LOCKED`（使用中）两种状态，小程序只有「可使用 / 已使用 / 已过期」三个页签且按状态向后端过滤 → **这两种券在所有页签里都查不到**，后台发到手的券会「凭空消失」。改法：小程序一次拉全量，页签本地过滤，「可使用」页签收纳 `AVAILABLE/NOT_STARTED/LOCKED`，卡片上再标出「未生效」「使用中」
- [✓] 缺口二：`my.vue` 优惠券数量只数 `AVAILABLE`，未生效的券不计数 → 「发了券但数量还是 0」。改法：数量口径与「我的优惠券」的「可使用」页签一致（还没用掉的券）
- [✓] 缺口三（后端）：后台定向发券填错用户 ID 时照样发一张券（`granted=1`），券没人看得到、运营还以为发成功了。改法：`CouponMapper.listExistingUserIds` 校验目标用户，发放前不存在直接 400「用户 ID 不存在：xxx」（全量发券不受影响）
- [✓] 本地实测：发券给不存在的用户 → 400「用户 ID 不存在：999999」；发给真实用户 → granted=1，C 端一次拉到 4 张券（2 张 AVAILABLE + 2 张 NOT_STARTED），「可使用」页签 4 张、我的页数量 4，口径一致
- [✓] 合成数据已清理（本地 `coupon_template` / `user_coupon` / `coupon_scope` 均归零），临时实例已停
- [ ] **发布生产**：`./deploy/release.sh order-service`（后端校验）+ 小程序 HBuilderX 重新构建上传

### 阶段 27：后台图片上传按展示规格压缩（2026-11-02，代码完成，待发版）

> 起因：用户问「图片上传的时候都不压缩吗？前端渲染压力不是，容易卡顿和白屏？」
> 核查结论：**上传确实不压缩，但"白屏"判断有误** —— 两端轮播都有静态图兜底，不会白屏；真正的问题是**尺寸完全没适配展示位**。

- [✓] 现状盘点（7 个真实上传点，全部走 `/api/admin/upload`）：
  - 后端 `UploadServiceImpl` 零压缩，只做 Content-Type 白名单 → UUID 命名 → 原样落盘；`product-service` 全局上限 50MB
  - 前端其实**已有** `utils/compressImage.js`（MAX_EDGE=1200 / jpeg q0.8），5 处 `a-upload` + 1 处富文本上传在用；但**全站一个硬编码规格**，没有按 type 区分
  - 现网那两张 banner 是 1200×1200，**正好等于 MAX_EDGE** → 走「边长不超过限制原样返回」分支，压根没压（这也解释了为什么是 webp 而非工具产出的 jpg）
  - **售后凭证图（`ShippedTable.vue` / `FinishedTable.vue`）完全没接 `:before-upload`**，手机拍照原图直传，是最大的未压缩黑洞
  - 压缩产物只有 `opt/upload`、`opt/body`、`opt/category` 三个目录（python 离线脚本产的）；banner/article/review/feedback/after-sale 无 opt，永远回落原图
- [✓] 新增规格表 `e-commerce/src/utils/image-spec.js`（按 type 给出目标尺寸 + 适配方式），尺寸 = 展示像素 × 2：
  - `banner` 按 position 分档：1 小程序首页 750×375 / 2 小程序分类页 750×200 / 11 PC 首页 1600×700
  - `article` 1200×675、`category` 400×400、`upload` 800×800 白底方、`body` 与 `after-sale` 最长边 1200
- [✓] 改造 `utils/compressImage.js`：支持 `cover`（居中裁切填满）/ `contain`（等比缩放留白不裁）两种适配；统一输出 webp（浏览器不支持时回落 jpeg，并校验 `blob.type` 防静默回落成 png）；解码改用 `createObjectURL` 避开 base64 的 1/3 内存放大；不传 spec 时保持旧行为 → 既有调用点零改动
- [✓] 8 个上传点接入：Banner（按 position 动态取规格 + 切位置提示重传）、文章、分类、商品主图（新增/编辑）、富文本正文图、售后凭证（新增压缩）
- [✓] 顺带修 2 个缺陷：富文本的 10MB 体积判断挪到压缩之后（原图 12MB 压缩后只 200KB，之前会被误拒）；后端加 per-type 体积兜底（图片 5MB / 视频 50MB，防绕过前端直传大图）
- [✓] 逻辑实测：Node 桩掉 DOM 跑 55 条断言全绿（各 type 画布尺寸/绘制区域/编码格式/文件名后缀、gif 跳过、已达标小体积图原样返回、webp 不支持回落 jpeg、解码失败兜底）；后端 `mvn compile` 通过、后台前端 `npm run build` 通过
- [✓] 真实编码收益（用现网那张 1200×1200 banner 实测）：小程序首页位 58.2KB → **21.7KB**，像素 144 万 → 28 万（解码内存 5.76MB → 1.1MB）
- [ ] **发布生产**：`./deploy/release.sh product-service`（体积兜底）→ `./deploy/release-web.sh`（后台前端）
- [ ] **发布后清理旧素材**：现网那 2 张测试 banner（1200×1200）重新按新规格传一遍；旧文件 `upload/image/banner/` 下可删
- [ ] 后台正式录素材：Banner 图按建议尺寸传（方形图传进宽幅位会被上下裁掉，运营需传宽幅图）

### 阶段 28：后台商品评价管理模块 + C 端评价展示（2026-11-02，代码完成，已发布生产）

> 起因：需求文档要求开发【后台商品评价管理模块】（查/新增虚拟/编辑/回复/隐藏/删除/导出 Excel），
> 并补 C 端评价展示。方案与决策见仓库根目录 `review`（v3，已按实现回改）。
> 5 个拍板：Q1 需要补 C 端展示、Q2 采纳合规建议、Q3 挂「订单管理」、Q4 真实评价整条只读、Q5 生产库结构已核对。

- [✓] 需求与现状核查：需求说「已 ALTER 过」的 6 个字段**实际 3 个不存在**（`reply`/`reply_time`/`is_virtual`/`created_by`），
  另外 3 个（`visible`/`del_flag`/`update_time`）表里**已有同义列**（`status`/`is_deleted`/`updated_at`）→ 只需加 4 列
- [✓] 生产库核对（用户执行 `SHOW COLUMNS`）：与本地**结构完全一致**、从未 ALTER 过
- [✓] 数据库：`docs/sql/review/01-admin-review-schema.sql`（加 4 列 + **2 个索引**
  `idx_review_admin` / `idx_review_product_show`，全部 `information_schema` 幂等写法，MySQL 5.7 兼容）
- [✓] 数据库：`docs/sql/review/02-admin-review-menu.sql`（菜单 id **202**「商品评价」挂订单管理 parent_id=3，
  按钮 230~234；角色 2 运营全授权 / 3 客服只给回复）—— **本地 + 生产均已执行并校验通过**
- [✓] 后端 order-service：`AdminReviewController`（8 个接口）+ `AdminReviewService(Impl)` + `ProductReviewController`（C 端只读 2 个）
- [✓] 后端：`ReviewService` **只加** `listByProduct` / `summary` 两个读方法，**买家提交评价逻辑一行未动**
- [✓] 后端：真实评价不可编辑**双保险**（Service 抛 400 + SQL `WHERE is_virtual = 1`）
- [✓] 后端：虚拟评价 `order_id=0` / `user_id=0` / `created_by=admin.id`，
  商品名与图**后端从 products 快照取，不信任前端传值**
- [✓] 后端：导出 Excel（POI，13 列，RFC 5987 文件名，公式注入防护，超过 5000 条截断）
- [✓] 网关：order-service 路由加 `/api/admin/reviews/**` + `/api/reviews/**`；
  C 端路径刻意**不用** `/api/products/{id}/reviews`（会被排在前面的 product-service 规则抢走）
- [✓] C 端免登录白名单：`WebMvcConfig.EXCLUDE_AUTH_PATHS` 加 `/api/reviews/**`
- [✓] 后台前端：列表页 + 虚拟评价新增/编辑弹窗 + api + 路由 `/orders/review`；
  `image-spec.js` 补 `review` 档（最长边 800 / contain）
- [✓] 小程序：商品详情页评价区（评分条 + 评价列表 + 商家回复 + 视频播放器），插在「商品参数」之前
- [✓] PC 商城：商品详情页评价区（同样插在「商品参数」之前）+ **顺带补上一直没渲染的图文详情 body**
- [✓] 详情页三块顺序（已定）：**商品评价 → 商品参数 → 图文详情**（小程序与 PC 商城一致）
- [✓] 本地全链路实测：新增/编辑/回复/隐藏/显示/删除/导出 + 参数校验 + C 端联动全部通过
  （隐藏后 C 端立刻不显示、恢复后立刻显示、删除后详情 404 且 C 端不显示）
- [✓] 构建验证：`mvn -pl order-service -am compile`、`mvn -pl product-service -am compile`、
  后台 `vite build`、PC 商城 `vite build`、小程序 SFC 编译 + `node --check` 全过
- [✓] **发布生产**（2026-11-02，按序全部完成）：
  1. 生产执行 `docs/sql/review/01-admin-review-schema.sql` + `02-admin-review-menu.sql`
     （**先 `./deploy/backup-prod-db.sh` 备份** → `/www/backup/e_commerce_db_2026-11-02_1227.sql`，
     经 stdin 管道导入，服务器不落文件）—— ✅ 加 4 列 + 2 索引 + 菜单 202/230~234 + role 2/3 授权
  2. `./deploy/release.sh order-service` —— ✅ 镜像 `20261102-83aa72c-dirty`，容器 running
  3. `./deploy/release.sh gateway` —— ✅ 同 tag，容器 running
  4. `./deploy/release-web.sh`（后台前端，与阶段 27 的图片压缩一起发）—— ✅ `index-87362cae.js`
  5. `./deploy/release-mall.sh`（PC 商城）—— ✅
  6. 小程序 HBuilderX 重新构建 —— ✅ 12:55 导出 `unpackage/dist/build/mp-weixin`
     （**微信开发者工具自动拉起失败**，`cli.bat` 不在 PATH，需人工导入上传）
- [✓] **发布后补验**（全部只读、输出脱敏）：
  - curl `GET /api/reviews/product/7` 过网关免登录 → ✅ 200；VO 无 `orderId`/`orderNo`/`userId` ✅
  - 后台列表 / 详情 / 筛选（含 orderNo 精确 1·截断 1·不存在 0）/ 404 → ✅
  - Excel 导出 → ✅ 200 且魔数 `PK\x03\x04`（真 xlsx）；**仍待人工用 Excel 打开看内容**
  - 无 `product:review:*` 的账号调接口 → ✅ **403「无权访问该功能」**
  - 后台 SPA 路由 `www.jiemids.com/orders/review` → ✅ 200；商城 `/product/7` → ✅ 200
  - ⬜ 运营账号登录后「订单管理 → 商品评价」菜单可见性（库里权限已确认，页面侧需人眼）
- [ ] **【待人工】小程序上传**：把 `unpackage/dist/build/mp-weixin` 导入微信开发者工具并上传发布

### 阶段 29：售后审核改造 + 变更流水 + 操作日志审计（2026-11-03，代码完成，已发布生产，小程序待人工上传）

> 方案与决策见仓库根目录 `after-sales`（v3）。3 个拍板：复审 4 项枚举**删掉不做**、商家删除售后单**不记**流水、
> 生产 `refund_confirming` 订正时 `refunded_at` **取原 `refund_at`**。另按用户意见删掉 `refund_confirming` 状态
> （「确认退款」是操作不是状态，项目不接真实支付没有「等款到账」时刻）。

- [✓] 删 `refund_confirming` 状态（`AfterSaleStatusEnum` 剩 7 项）；`statusConstants()` 同步删 `refund_confirming` 与 `returned`
- [✓] `AfterSaleOperateTypeEnum` 重写为 **9 项**（code/tag/desc/logContent 四字段），一次操作 = 一个 operate_type + 一个 after_status
- [✓] `support/AfterSaleTransitions` —— **状态机唯一真相**：10 条 (类型, 当前状态, 目标状态, operate_type, 按钮文案, 附加条件) 规则
- [✓] 后端状态流转校验：非法流转报「不允许从「X」变更为「Y」」；拒绝售后时商家备注必填
- [✓] `after_sale_log` 表 + `AfterSaleLogMapper` + `@AfterSaleLogRecord` 切面（**同步写、同一事务**，与 `operation_log` 的异步写不同）
- [✓] `operation_log` 表 + `common` 的 `ecommerce/log/` AOP 基建（`@OperationLog` + 切面 + 异步 `JdbcTemplate` 写入）
- [✓] `UserContext` 加 `CURRENT_TYPE`（admin/user），`JwtInterceptor` 写入；`remove()` 一起清（Tomcat 线程复用会串号）
- [✓] `JdbcOperationLogRecorder` 用 `ObjectProvider<JdbcTemplate>` —— **file-service 依赖 common 但无 JDBC**，直接注入会启动失败
- [✓] C 端 `AfterSaleVO` 不再返回 `merchantRemark`（原来 C 端也能拿到商家内部备注）
- [✓] 后台 3 个 `*Review.vue`：删审核结果下拉，改成**后端下发 `availableActions` → 前端按 kind 渲染主/危险按钮**；
  修掉 `confirmForm.value = []`（对象被赋成数组）、`returning_exchange`/`exchange_shipping` 死状态、蛇形字段名 `exchange_at`→`exchangeAt`
- [✓] 后台 3 个 `*Detail.vue` + 小程序 3 个 detail 页 + PC 商城 `AfterSaleDetail.vue`：加「变更记录」时间线
  （后台用新组件 `src/components/AfterSaleLogTimeline.vue`；小程序用新组件 `components/after-sale-log-list/after-sale-log-list.vue`）
- [✓] 数据库（**本地 + 生产均已执行并复核**）：
  - `docs/sql/log/01-operation-log-schema.sql`（建 `operation_log`）
  - `docs/sql/after-sale/01-after-sale-log-schema.sql`（建 `after_sale_log`，**由杰米手动建好**，脚本核对过结构与 mapper 一致）
  - `docs/sql/after-sale/02-fix-only-refund-returning.sql`（生产 1 行）
  - `docs/sql/after-sale/03-fix-refund-confirming.sql`（生产 2 行；**子表先于主表**，否则定位不到）
  - 生产备份 `/www/backup/e_commerce_db_2026-11-03_1008.sql`（校验通过）
- [✓] 编译验证：`mvn -o install -DskipTests` 全量通过
- [x] **已发布生产**（2026-11-03）：镜像 tag `20261103-00c8111-dirty`，6 个容器全部 running，启动日志无 error/exception；
  后台前端 `release-web.sh` → `index-bc9b658c.js`；PC 商城 `release-mall.sh` → 完成
  - 发布后只读验证：状态字典接口已无 `refund_confirming`/`returned`；两个新日志接口未登录返回 401（路由已注册、鉴权生效）
- [ ] **【待人工】小程序发版**：HBuilderX 重新构建 → 微信开发者工具上传（**买家侧「处理进度」是新页面，必须重发**）

## 风险与待确认

| # | 风险/待确认 | 影响 | 处置 |
|---|---|---|---|
| 1 | 每服务独立库 vs 共享库 | 事务边界与 Feign 复杂度 | 单店共享库，后续按需拆库 |
| 2 | ~~沙箱回收的跨服务一致性~~ | — | 已随多租户下线 |
| 3 | 现有 `user`（小程序）与 `admin`（后台）账号体系并存 | 两套登录入口 | admin 走 auth-service，user 走 user-service，JWT type 区分 |
| 4 | Spring Cloud 组件在 Boot 4 下的调试成本 | 单人项目进度 | 先最小骨架再铺开 |
| 5 | 后台断线重连 / 跨实例推送 | 多实例下 WS 钉死单机 | 阶段 11 用 RabbitMQ fanout 广播 |
| 6 | 宝塔托管的站点 conf 会被重写 | 商城 443 SSL 块被冲掉 → 首页 404（2026-10-31 已发生） | 已补 443 块；需在宝塔打开该站点 SSL 开关，并把关键配置放 `extension/` 目录 |
| 7 | 边缘缓存会缓存错误响应 | 源站已修但用户仍看到旧内容（商城首页曾缓存 nginx 404 页） | 巡检按用户视角检查；发现后 EdgeOne 刷新缓存（TTL 到期也会自愈） |
| 8 | `xiaojiecpn.com` 证书 2026-11-29 到期 | 到期后 EdgeOne 回源 HTTPS 失败 → 首页 404 | 宝塔设自动续签或提前换证 |


## 历史错误

已解决的历史错误不再堆在这里，见 `findings.md` 对应日期章节（CORS 重复、MySQL 3306/3307、Nacos 起不来等）。
