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

### 阶段 0：基础设施 + 版本兼容验证
- [✓] Maven 多模块父工程 + `common` 框架层（Result/异常/JwtUtil/UserContext/JwtInterceptor）
- [✓] Spring Cloud + SCA + Nacos + Gateway 搭建，Boot 4.1 兼容性验证通过，网关路由打通
- [✓] 全局 Jackson `LocalDateTime` 统一 `yyyy-MM-dd HH:mm:ss`（2026-10-23）
- **状态：** completed

### 阶段 1：gateway + auth-service
- [✓] admin 登录/登出/info 接口 + admin 表种子（admin/admin123）
- [✓] gateway CORS（WebFlux `CorsWebFilter` + 去重 `GlobalFilter`）
- [✓] 后台管理端登录端到端跑通
- [ ] RBAC 权限表（role/permission）
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
- [✓] 分类重构 8 一级 / 21 二级；一级/二级都支持商品过滤
- [✓] 商品整理为 126 件（50 现有 + 76 去重种子 id 51~126，每叶子 6）并已上生产
- [✓] C 端分页 `GET /api/products/page`（分类/关键词/品牌/价格/排序），旧接口保持数组兼容
- [✓] `products.description` 重命名为 `title`（主标题），商品 title 改为 SEO 长标题；`sub_title` 保留
- [✓] 商品 `sub_title` 淘宝风格化：营销销量/加购 + 可开发票 + 品类口碑，一句话 17~24 字（2026-10-29）
- [ ] 前端接 specs / 分页 / 分类侧边栏（小程序 → 后台 → PC）
- [ ] parameters 表 + 参数组/参数项
- [ ] 促销 `business_onsale` 表 + 活动 CRUD（含定时下架）
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
- [ ] 运营数据导出上线：新版 order-service 已随 TCR 镜像部署；待上传新后台 `dist` + 清缓存 + 验证 xlsx 下载
- [ ] 订阅消息上线：生产库 `orders` 两列 + `idx_orders_unpaid_notify` 已确认存在，order/user 已随 TCR 部署；待小程序发行后验证推送
- [ ] 后台管理端 `dist` 重新构建上传（sandboxVisitor、订单金额标签），否则沙箱复用不生效
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
- [ ] 分类图收尾：webp 已上传、生产库已改 `.webp`；待重建 product/user 容器 + 清 CF 缓存
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
- [ ] AI 下单闭环（写操作需二次确认，当前不做）
- [ ] RAG / 商品卡片渲染（前端只渲染纯文本，需前后端一起改）
- **状态：** in_progress

### 阶段 10：安全加固（DDoS 防护）

**应用/网关层（自己落地）：**
- [✓] AI 接口鉴权与限流（2026-10-26，Python 侧）：强制 JWT + 每用户 10 分钟 20 次 / 每天 100 次 + 全局并发 10 + 单次 token 上限
- [ ] 网关限流（`RequestRateLimiter` Redis 令牌桶 + 按 IP 的 `KeyResolver`）
- [ ] 分接口差异化限流（免登录浏览/上传收紧）
- [ ] 兜底硬限制（请求体大小、网关 connect/response timeout、连接数/线程上限）
- [ ] IP 黑名单 + 滑动窗口计数（Redis + Lua）

**网络/基础设施层（上线前补）：**
- [ ] 静态资源迁 CDN（`/image/**`、`/upload/**`）
- [ ] 网关前套高防 IP / WAF

- **状态：** pending

### 阶段 11：后台实时提醒（WebSocket）
- [✓] 单实例版：`order-service /ws/order` + 握手鉴权 + 单集合广播 + 支付成功推送 + 前端弹窗/语音/心跳/重连
- [✓] RabbitMQ 最小 demo（2026-10-29，仅 dev）：`order-service` 加 `spring-boot-starter-amqp`，`demo.exchange/demo.queue` 启动自动发一条并消费打日志
- [ ] RabbitMQ fanout 跨实例广播（替代单机内存注册表，解 `lb://` 长连接钉死单实例）
- [ ] 事件补齐：发货、新售后单
- **状态：** in_progress

### 阶段 12：微信支付接入（真实支付）
- [ ] `payOrder` 改 JSAPI 下单（拿 `prepay_id`，此时不改订单状态）
- [ ] 支付回调 `POST /api/pay/notify`：验签 → 幂等 → **金额校验** → 推进状态 + 落流水
- [ ] 主动查单兜底：前端支付成功跳回时先查单确认
- [ ] 定时查单/日终对账（扫「已扣款未回调」订单）
- [ ] `pay_record` 支付流水表
- [ ] 微信支付凭据走环境变量（`docs/env.md`）
- **状态：** pending（当前 `payOrder` 仍是 mock 状态流转）

## 定时任务规划（Spring Task）

### 档 1：有实体，可直接做

| 任务 | 归属服务 | 触发方式 | 状态 |
|------|---------|---------|------|
| 超时未支付订单自动取消（30min 恢复库存 + 改取消） | order-service | `fixedDelay=60000` | ✅ 已实现 |
| 订单超时自动确认收货（发货满 7 天，`sign_status=auto`） | order-service | `fixedDelay=60000` | ✅ 已实现 |
| 订单未支付提醒（失效前 10 分钟，一次性订阅消息） | order-service | `fixedDelay=60000` | ✅ 已实现（待上线）；后续计划改 RabbitMQ 延迟队列 |
| ~~沙箱过期回收~~ | ~~shop-service~~ | — | ❌ 已随多租户下线（2026-10-29） |
| 定时下架过期活动商品 | product-service | 每天 | ⏳ 待做（依赖 `business_onsale`） |

### 档 2：需先建表 / 接第三方

| 任务 | 归属服务 | 前置依赖 |
|------|---------|---------|
| 每日统计报表入库 | 待定 | 统计报表表 + admin 报表页 |
| 清理过期优惠券 | 待定 | `coupon` 表 |
| 清理过期临时文件 / 失效购物车 | file-service / order-service | file-service 未做 |
| 会员到期提醒 + 营销消息推送 | user-service | 会员等级表 + 订阅消息模板 |
| 第三方物流状态同步 | order-service | 接物流 API（当前假数据） |
| 微信支付主动查单/对账 | order-service | 阶段 12 + `pay_record` |

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
- [ ] P2：C 端 demo 登录（user-service，dev profile，13800138000）+ 购物车 + 地址
- [ ] P3：下单/模拟支付/订单中心
- [ ] P4：收藏、评价列表、售后、AI 客服
- **状态：** in_progress（首版已上线，交易链路未接）

## 风险与待确认

| # | 风险/待确认 | 影响 | 处置 |
|---|---|---|---|
| 1 | 每服务独立库 vs 共享库 | 事务边界与 Feign 复杂度 | 单店共享库，后续按需拆库 |
| 2 | ~~沙箱回收的跨服务一致性~~ | — | 已随多租户下线 |
| 3 | 现有 `user`（小程序）与 `admin`（后台）账号体系并存 | 两套登录入口 | admin 走 auth-service，user 走 user-service，JWT type 区分 |
| 4 | Spring Cloud 组件在 Boot 4 下的调试成本 | 单人项目进度 | 先最小骨架再铺开 |
| 5 | 后台断线重连 / 跨实例推送 | 多实例下 WS 钉死单机 | 阶段 11 用 RabbitMQ fanout 广播 |

## 历史错误

已解决的历史错误不再堆在这里，见 `findings.md` 对应日期章节（CORS 重复、MySQL 3306/3307、Nacos 起不来等）。
