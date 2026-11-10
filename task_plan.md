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
| 数据库变更同步 | **本地执行完必须同步生产**（2026-11-05 明确写入）：发版只换代码、不带数据；写库两边都要跑并核对行数（详见 `findings.md` 顶部「硬规则」） |

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
| `monitor-service` | 系统监控（服务器/缓存/在线用户/动态定时任务；经 Nacos 直连各服务 Actuator 采集） | admin_monitor_task + Redis | 9008 |
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
- [✓] 物流轨迹过滤未来时间（2026-10-26）：`buildLogistics` 只返回当前时间之前已发生的轨迹（`addTraceIfPast`）—— **已被 2026-11-04 的滚动时间轴取代**（当时只修了 C 端，后台那份漏了；现已抽成 `support/LogisticsBuilder` 共用）
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
- [✓] 迁移到 Python（2026-10-26）：新增 FastAPI + LangGraph 服务 `ecommerce-python`，多轮对话 + 商品问答（直连 MySQL 只读）；网关 `/api/agent/**` 静态转发；前端契约不变
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
- [✓] 主键号段压平：沙箱段 8 位 id（>=100000）改回连续小号，级联改 17 个引用列 + 归位 AUTO_INCREMENT（2026-11-01，本地+生产均已执行；脚本 `docs/sql/normalize-sandbox-ids.sql（该脚本已从 docs/sql 删除，见 git 历史）`）
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
  - `docs/sql/after-sale/02-fix-only-refund-returning.sql（该脚本已从 docs/sql 删除，见 git 历史）`（生产 1 行）
  - `docs/sql/after-sale/03-fix-refund-confirming.sql（该脚本已从 docs/sql 删除，见 git 历史）`（生产 2 行；**子表先于主表**，否则定位不到）
  - 生产备份 `/www/backup/e_commerce_db_2026-11-03_1008.sql`（校验通过）
- [✓] 编译验证：`mvn -o install -DskipTests` 全量通过
- [x] **已发布生产**（2026-11-03）：镜像 tag `20261103-00c8111-dirty`，6 个容器全部 running，启动日志无 error/exception；
  后台前端 `release-web.sh` → `index-bc9b658c.js`；PC 商城 `release-mall.sh` → 完成
  - 发布后只读验证：状态字典接口已无 `refund_confirming`/`returned`；两个新日志接口未登录返回 401（路由已注册、鉴权生效）
- [ ] **【待人工】小程序发版**：HBuilderX 重新构建 → 微信开发者工具上传（**买家侧「处理进度」是新页面，必须重发**）

### 阶段 30：工作台用户统计口径收口（2026-11-03，代码完成，待发版）

- [✓] 排查结论：工作台「用户增长」图**数据来自后端**，不是前端算的
  （`GET /api/admin/users/statistics` → user-service `UserServiceImpl.getUserStatistics` → 两条同表同过滤的注解 SQL）
- [✓] 「新增用户比总用户还多」是**双轴视觉误导**，数值上不可能越界（Σ新增 ≤ totalUsers）
  - 生产只读核对：65 人全未注销、openid 无重复、无未来时间脏数据，近 7 天新增 37 → 累计 28→65
  - 机制：双轴各自缩放 + 右轴刻度数字被 `UserGrowth.vue` 藏掉 + 两条线量纲不同（存量 vs 单桶增量）
- [✓] **方案 A（用户拍板）：累计曲线搬回后端**（**保留 `cumulativeUsers` 这一半**）
  - `UserStatisticsVO` 新增 `cumulativeUsers`；`UserServiceImpl.getUserStatistics` 一次算完
    （`base = max(0, totalUsers − Σ窗口新增)`，逐桶累加，**末值恒等于 totalUsers**）
  - 前端 `UserGrowth.vue` 直接用后端值（保留老接口兜底），`useWorkplaceData.js` 解析新字段
- [✓] **追加（用户拍板）：去掉双轴** —— 两条序列共用一根纵轴（0~65）
- [✓] **再拍板：折线 + 柱状**
  - 「用户总量」= 存量累计 → **折线**（连续量，看趋势）
  - 「每日新增」= 每桶新增 → **柱状**（离散量，柱子高度就是那天的量）
  - `LineChart` 组件加 `type` 支持：序列可指定 `'line'`（默认）/ `'bar'`（`barMaxWidth: 24`），
    其余图（营业额、订单统计）不传 `type`，行为不变
- [✓] **中途废弃的方案（勿再走）**：「两条线都画累计」（累计新增 = `newUsers` 前缀和）——
  两条累计曲线形状雷同、第二条只是平移，信息量极低，等于重复造轮子；
  后端 `cumulativeNewUsers` 字段**已删除**（未发版字段，删零风险）
- [✓] 验证：`mvn -o install -pl user-service -am` 通过、后台 `vite build` 通过、
  本地临时实例（9008，禁注册 nacos）接口实测：字段只剩 `bucketType/buckets/newUsers/cumulativeUsers/totalUsers`、
  存量末值 == totalUsers、单调递增、`cumulativeNewUsers` 已不存在
- [ ] **【待发版】** user-service 镜像 + 后台前端 `release-web.sh`（用户选择「先不发」）

### 阶段 31：后台登录锁定排查 + 工作台图表收尾（2026-11-03，结论已定）

- [✓] **「失败 5 次锁 10 分钟」实测：机制正常，"没锁"是观感错觉**
  - 直连 9001 与经网关 8080 两条路径、每次换新验证码：第 1~4 次 `401`，Redis 计数 `auth:login:fail:admin:{ip}` 1→4（ttl=600），**第 5 次 `429`**，锁定后正确密码仍 `429`
  - **对照实验**：验证码错误 → `401 验证码错误或已过期`，**失败计数 key 为空**（校验在密码之前抛异常，走不到 `recordLoginFailure`）
  - 观感来源：前端失败后刷新验证码图并清空输入框，一旦有一次输错新码，计数永远停在 1
  - 行为特征：① 锁定后正确密码也进不去；② 计数 key 带 IP（经网关 = 网关地址），换 IP 重新计；③ 账号不存在也计数
- [✓] **登录加固三条按用户决定暂缓**（`AuthServiceImpl` **加固部分零改动**）：dummy BCrypt 消除时序侧信道、IP 维度限流、验证码 delete 异常分级
- [✓] **锁定提示语补上失败次数**（杰米指出「怎么没有这句提示」）
  - 这句「登录失败次数已超过%d次，已被锁定，请%d分钟之后再试」**项目里从来没存在过**（后端 + 三个前端 + 文档全局搜过），原先只有「尝试次数过多，请 10 分钟后再试」
  - 改法：`lockedMessage(Long ttl)` → `lockedMessage(long failures, Long ttlSeconds)`，
    用 `String.format` 输出；两个调用点（`assertNotLocked` 被拦 / 密码错刚好到阈值）都带次数与剩余分钟
  - **前端不用改**：`request.js` 的 429 分支直接展示后端 message
  - 实测：第 1~4 次 `401 用户名或密码错误`；第 5 次 `429 登录失败次数已超过5次，已被锁定，请10分钟之后再试`；锁定后再试同一句（剩余 599 秒 → 向上取整 10 分钟）
- [ ] **【需人工】重启本地 auth-service**（旧进程仍是旧文案）+ 随下次发版上生产
- [✓] **「前端没显示用户图表」定性**：本地 IDEA 的 user-service（PID 21588 / 9006）是改代码前启动的旧进程，
  `/api/admin/users/statistics` 返回 **500** → 前端 catch → `userGrowth` 空数组 → `LineChart` 显示「暂无数据」
  - 证据：同进程其它 DB 接口（`member-levels`/`members`）200，**只有 statistics 500**；最新 jar 临时实例（9008）同库同 SQL 直接 200，两个累计字段都对
- [✓] **前端补错误态**（唯一的代码改动）
  - `useWorkplaceData.js`：新增 `userGrowthError`（失败置 `true`，同时保留上一次数据，图表不塌成空白）
  - `UserGrowth.vue`：新增 `error` prop，报错时显示「用户统计加载失败，请刷新重试」并占满图表高度（400/340/260）
  - `Workplace.vue`：透传 `:error="userGrowthError"`；`vite build` 通过
- [ ] **【需人工】在 IDEA 里重启 user-service**（旧进程 9006，重启后本地图表即出图）
- [ ] **【待发版】** 阶段 30 的 user-service 镜像 + 后台前端 `release-web.sh`（用户选择「先不发」）

### 阶段 32：发货管理模块方案改写（2026-11-03）
- [✓] **原方案审阅**：仓库根 `express` 文件是「发货管理模块」原始方案（3 张表 DDL + 枚举/实体/Mapper/Service/Controller + 前端两页）
  - **致命问题**：原方案按 MyBatis-Plus 写（`BaseMapper`/`IService`/`@TableName`/`Wrappers.lambdaQuery`），
    而 `order-service` 的 pom **只有 `mybatis-spring-boot-starter`，无 MyBatis-Plus** → 原方案的 Java 代码一行都编译不过
  - 另 29 处与项目约定冲突（`Orders`→`Order`、`deleted`→`is_deleted`、creator/updater 全项目没有、
    `RuntimeException`→`BusinessException`、`@RequestHeader("username")`→`UserContext.getUserId()`、
    `Result<List>`→`Result<PageVO<T>>`、无 `@RequirePerm`、定时任务位置、网关 Path 未加 等）
- [✓] **3 个最隐蔽的坑**（写代码时最容易踩）：
  - ~~`orders.confirm_at` 实际是**商家确认收款时间**（`updateStatus` 在 status=3 时写），DDL 注释「确认收货时间」是历史遗留错误 → 禁止拿它记签收~~（2026-11-04：该列已随「取消商家确认环节」删列，见 `docs/sql/drop-confirm-at.sql（该脚本已从 docs/sql 删除，见 git 历史）`）
  - `orders.sign_status` 值域是英文 `signed`/`auto`/`unsigned` → 禁止写中文；且本期**不写**它（会与 7 天自动完成抢写）
  - 订单状态必须走 `orderMapper.updateStatus(...)`（XML 按目标状态自动写 `ship_at`/`courier`/`tracking_number`），手动 set 会漏字段
- [✓] **方案已改写**：`express` 文件重写为项目版（正文 = 唯一执行依据）
  - **2026-11-03 二次精简（杰米：「我看得好痛苦，把不需要做的都去掉，留下需要做的，并且核心的」）**：在第一次精简（2161 → 1512 行）基础上再砍一轮 ——
    删除 **§13 行业通用做法取舍对照表**（整表）、§1 的 19 条「现状分析」压成 15 条「实现约定」、§8.0 的 19 行问题清单压成 7 行「旧页的坑」、
    §5 的实体类全文 / Mapper 全量方法清单、几段 Java 骨架改为规则描述 —— **全文只剩要执行的内容：1512 行 → 1013 行**
  - **2026-11-03 三次精简（杰米：「我没接入真实支付的API，不会生成真的物流轨迹，也不可能采用手工录入，我采用自动编的就好了」）**：
    **物流轨迹整套删除** —— 轨迹表 `order_delivery_trace`、手工录入接口 + 抽屉、`ExpressTraceProvider` + no-op 实现、
    `syncTraces()` 定时任务、轨迹状态枚举与归一化关键词表、三级降级读取、`order:delivery:trace` 权限码、包裹 `status` 列
    全部砍掉；**mock 物流 `buildLogistics` 原样保留**（C 端 `logistics.vue` 与 `GET /api/orders/{id}/logistics` 一行不改）。
    **表从 4 张减为 3 张**（`delivery_express` / `order_delivery` / `order_delivery_log`），权限码 7 → 6 个，前端验收 11 → 9 条 —— **1013 行 → 866 行**
  - **2026-11-03 四次精简（杰米：「把不需要做的事情和多余的废话都去掉，我只要看该做什么」）**：
    删掉 §13 行业对照表残留、现状分析、实体类全文、Java 骨架；3 张表 DDL **一字未改**保留；
    章节重排为「拍板结论 → §0 做什么 → §1 实现约定 → §2 数据表 → §3 后端 → §4 前端 → §5 权限与网关 → §6 落地顺序 → §7 不做」—— **866 行 → 433 行**
  - **2026-11-03 五次精简（杰米：「你节省到300行以内」）**：
    菜单 SQL 顺延 UPDATE 合并成一条 CASE、4.4 表改列表、4.2 硬性要求 13 → 11 条、4.5 验收 9 → 7 条、
    验证点 9 → 8 条、巡检 SQL 与网关 Path 改单行 —— **433 行 → 290 行**
  - 含：3 张表 DDL、枚举、Mapper 方法清单、Service 接口、Controller 权限码表、前端两页硬性要求、
    菜单与权限 SQL、网关 Path、落地顺序（P0~P4）、验证点清单、前端验收清单（7 条）、明确不做清单
  - 后端归 `order-service`（不新建服务），前端归 `e-commerce/src/pages/delivery/`
- [✓] **补齐原方案漏掉的三块**（杰米追问「行业通用做法看了吗 / 日志怎么处理 / 库存怎么处理 / 轨迹落不落库」后补）：
  - **发货业务流水表 `order_delivery_log`**（§2.3 / §3.4）——`operation_log` 是异步写且**无查询接口**，后台查不到「谁发的货」；
    照抄售后域 `after_sale_log`（**同步同事务**、页面能读）补一层，后台包裹页可查时间线
  - **物流轨迹**（**2026-11-03 拍板后删除**）——原设计要落库 + 三级降级读取；用户澄清「没接真实支付API 不会有真轨迹、也不会手工录入，
    就用自动编的」→ 整套删除，mock `buildLogistics` 原样保留
  - **库存边界声明**（§3.4）——本项目**下单时就扣库存**（乐观锁 `deductStock`），超时未付/取消才恢复；
    **发货阶段不扣、不校验库存**（发货时校验会把订单永久卡在待发货）；售后退款/退货**不回补库存**是既有现状，本模块不动
  - 行业通用做法里的「安全与合规」4 条也补进 §7 明确不做清单：操作回滚/撤销发货 **不做**（改状态机唯一真相）、二次验证 **不做**、
    权限分离 **已做**（6 个权限码分开）、合规报告 **不做**
- [✓] **用户已拍板 3 项**：① 快递公司**建字典表** `delivery_express`（`CourierEnum` 保留作兜底不删）② **不接**外部快递 API（Provider 留 no-op 骨架）③ 旧 `/orders/ship` 发货管理页**下线**
- [✓] **前端实地核对**（2026-11-03，杰米指出「前端发货管理逻辑是错的，后端当时还是 AI mock，也没有消费者环节」）：
  - 核对了 `e-commerce/src/pages/orders/shipment/` 4 个文件（`Ship.vue`/`PendingShipTable.vue`/`ShippedTable.vue`/`FinishedTable.vue`）+ 后端 `order-service` 对应实现
  - **3 个真 bug**：① 已付款订单卡在 status=2 发货页看不到（「确认收款」只在订单列表页）→ 商家看到"待发货 0 单"
    ② 售后按钮 `has_after_sale` 与后端 `afterSale` 字段名错位 + SQL 没查 + toVO 没赋值 → **永不置灰，同一单可重复提交售后**
    ③ 发货接口不校验快递公司/单号 → **空单号也能发货**
  - **6 类语义错**：签收状态恒为「已签收」（没区分 `signed`/`auto`）、商家代确认收货被记成买家签收（**已拍板：删按钮 + 下线 `PUT /api/admin/orders/{id}/finish`，后台对签收只读**）、
    物流轨迹是 `order.getId()%97` 现算的假数据（且高亮的其实是「已揽件」第一条）、列名「订单确认时间」实为收款确认时间、
    列名「配送信息及备注」实为买家留言（且**没显示收货地址和手机号**）、已取消/退款中订单在发货页不可见
  - **通用欠账**：无分页（`pageSize` 硬编码 1000）、错误提示重复弹 10 处、`FinishedTable` 漏 `hasPermi` 判断、
    发货页塞了取消/删除/批量删除（后台取消会**恢复库存 + 自动生成整单退款单**）、发货弹窗无商品明细
  - **结论已写入 `express`**（章节号按 2026-11-03 精简后的新结构）：§4.2（页面硬性要求 11 条）、§4.4（需后端配合的前端改动 3 条）、§4.5（前端验收清单 7 条）、§3.3（发货/补发规则）、§3.4（签收/轨迹/流水/审计/库存）、§3.5（事务边界 + 并发安全：3 个写入口用事务、`updateStatus` 无状态条件 → 新增 `updateStatusIfCurrent` 条件更新，防并发双击插出两个包裹）、§3.6（巡检 SQL + 工作台 4 个数字）、§5.1/§5.2/§5.3（菜单 SQL、权限码与角色、网关 Path）、§6（落地顺序 P0~P4）、§7（明确不做清单）；另同步更新 §0（这次要做的事）、§1（实现约定）、§2（3 张表 DDL）
- [✓] **用户拍板开工**（2026-11-03：「可以动手了」+「我授权给你」→ 生产库变更已授权）→ 按 P0~P4 实施
- [✓] **P0 基础设施**（本地库已执行并核对）
  - `docs/sql/shipment/01-shipment-schema.sql`（3 张表：`delivery_express`/`order_delivery`/`order_delivery_log`）
  - `docs/sql/shipment/02-delivery-express-seed.sql`（8 家快递种子，code 用英文 key，`ON DUPLICATE KEY UPDATE`）
  - `docs/sql/shipment/03-delivery-inspect.sql`（巡检：已发货超 7 天未完成）
  - `docs/sql/rbac/04-delivery-menu.sql`（**文件名从方案里的 `02-delivery-menu.sql` 改**：与既有 `02-drop-admin-role-column.sql` 冲突）
  - 网关 `gateway/src/main/resources/application.yml`：order-service 路由 Path 追加 `/api/admin/deliveries(/**)`、`/api/admin/expresses(/**)`
  - 本地库核对通过：3 张表已建、8 家快递已种、菜单 201 改顶级 M 型目录（`/delivery`，sort 4）、顶级顺延为 售后5/库存6/经营工具7/个人中心8/系统9、
    新菜单 833-838（快递发货列表/快递公司 + 4 个按钮权限）
- [✓] **P1 快递公司字典**
  - 后端：`DeliveryExpress` 实体/Mapper/XML/Service/VO/DTO + `AdminExpressController`（`/api/admin/expresses`）
  - `GET /api/admin/orders/couriers` 改读 `delivery_express`（形状不变 `[{value,label}]`，`CourierEnum` 保留作 mock 物流兜底）
  - 上传改造：`UploadServiceImpl.TYPE_SUBDIR` 加 `express`、`AdminUploadController.assertPermission` 加 `case "express" -> order:express:save`、
    `image-spec.js` 加 `express: {120×120, contain, #ffffff}`
  - 前端：`src/api/delivery.js` + `src/pages/delivery/ExpressList.vue`（编码/名称搜索、logo 上传、启用状态、a-popconfirm 删除）
- [✓] **P2 发货主流程**
  - 后端：`OrderDelivery`/`OrderDeliveryLog` 实体 + Mapper + XML + DTO + VO + Service + `AdminDeliveryController`
  - `AdminOrderServiceImpl.shipOrder` 改为委托 `deliveryService.ship(...)`（老接口 `PUT /api/admin/orders/{id}/ship` 路径契约不变）
  - `order_delivery_log` 同步同事务写流水（发货/补发/改单三处），`operator_id` 取 `UserContext.getUserId()`
  - `OrderMapper.listAdmin` 补 `after_sale` EXISTS 子查询 + `toVO` 赋 `.afterSale(...)`（售后按钮置灰的前提）
  - 前端：`src/pages/delivery/DeliveryList.vue`（2 个 Tab：快递发货列表 + 待发货订单；分页；发货弹窗带收货地址+商品明细；二次确认；流水抽屉）
- [✓] **P3 并发安全与巡检**
  - `OrderMapper.updateStatusIfCurrent(id, fromStatus, ...)` 条件更新（`WHERE id AND order_status`），影响 0 行 → 409「订单状态已变更，请刷新后重试」
    （**`updateStatus` 语义不动**）
  - 3 个写入口（ship/resend/update）都有 `@Transactional(rollbackFor = Exception.class)`，包裹 + 订单状态 + 流水同生共死
  - 改包裹时**仅首包裹**同步 `orders.courier/tracking_number`（补发包裹改了不能把订单指过去）
  - **【运行时实测修正】写包裹必须排在条件更新订单状态之后**：`order_delivery` 有指向 `orders` 的外键，
    先 `INSERT` 子表会对父行加**共享锁**，与并发 `UPDATE orders` 的**排他锁**形成 InnoDB 锁顺序反转 →
    并发双击实测 **死锁 → HTTP 500**（`MySQLTransactionRollbackException: Deadlock found`）。
    改为「先条件更新 orders（拿父行排他锁）→ 再插包裹 → 再写流水」后，
    6 个订单各跑一次并发双击全部得到 **[200, 409]**、每次只产生 1 个包裹
- [✓] **P4 收尾**
  - 路由切换：`src/router/index.js` 加 `/delivery` 顶级路由（子路由 `list`/`express`，`meta.permission` = `order:delivery:list`/`order:express:list`），
    **移除** `/orders/ship`；旧 `Ship.vue`/`ShippedTable.vue`/`FinishedTable.vue`/`PendingShipTable.vue` 4 个文件已删
  - 下线 `PUT /api/admin/orders/{id}/finish`：删 `AdminOrderController.finish` + `AdminOrderServiceImpl.finishOrder` + `AdminOrderService.finishOrder` + 前端 `api/orders.js` 的 `finishOrder`
  - 旧页遗留收口（随旧页下线 + 新页实现）：签收三态渲染（`signed`/`auto`/`unsigned`）、无「代客确认收货」入口、
    「订单确认时间」改名「收款确认时间」、补收货地址/手机号/买家备注三列、去掉取消/删除/批量删除、
    行内按钮 `hasPermi` 与 `admin_menu.perms` 一字不差、业务 catch 不重复弹 `message.error`
  - `@OperationLog` 审计：发货/补发/改单/字典增改删 6 处
  - 验证：`mvnw -o compile`（order-service + product-service）通过、前端 `vite build` 通过、本地库 3 表 + 种子 + 菜单已核对
  - **运行时冒烟**（临时实例跑在 9104，禁用 Nacos 注册避免污染用户 IDEA 里的 9004 进程）：
    26 项接口断言全通过 —— 字典列表/启用列表、包裹列表、`/api/admin/orders/couriers` 返回 8 条 `[{value,label}]`、
    待确认(2)发货 400、空快递公司 400「请选择快递公司」、空单号 400「请填写快递单号」、未在字典的快递公司 400、
    禁用快递公司后发货 400「快递公司不存在或未启用」且不产生包裹、发货后 `order_status=4` + `ship_at/courier/tracking_number` 有值 +
    `confirm_at` 不变 + `order_delivery` 1 条 + `order_delivery_log` 1 条（`operator_id` = 当前管理员）、
    补发后 `orders` 无变化 + `order_delivery` 多 1 条 `delivery_type=2`、改单号只同步首包裹到 `orders`、
    流水 3 条（发货/改单/补发）、字典增改删、无 `order:delivery:list/ship/resend`、`order:express:list` 权限一律 403
- [✓] **对方案的 4 处细化**（实施时发现，按项目实际调整）
  1. **warehouse（仓储）角色也授发货相关权限**：该角色原本持 `order:order:ship`，旧页下线后若不授权会失去发货能力，与方案「发货是运营/仓库的事」一致
     → operator 全套；warehouse 给 `order:delivery:list/ship/resend` + `order:express:list`；service 只给 `order:delivery:list`（只读）
  2. **新增 `PUT /api/admin/deliveries/{id}`**（改包裹快递信息）：方案 §3.2 接口表漏了这条，但 §4.2 要求列表有「编辑」按钮 → 补接口，复用 `order:delivery:ship` 权限码，不新增权限码
  3. **RBAC SQL 文件名 `02-delivery-menu.sql` → `04-delivery-menu.sql`**：既有 `docs/sql/rbac/02-drop-admin-role-column.sql` 已占用 02
  4. **事务内「条件更新订单状态」排在「写包裹」之前**：见 P3 的死锁修正（外键共享锁与父行排他锁锁顺序反转）
- [✓] **生产库已同步**（用户授权「我授权给你」）：3 张表 + 8 家种子 + 菜单改造 **同一份 SQL** 执行并核对
  （菜单 201/833-838、couriers=8、deliveries=0、角色授权 operator 6 / service 1 / warehouse 4）
- [ ] **【待发版】** order-service / product-service / gateway 镜像 + 后台前端 `release-web.sh`
  （网关 Path 不加就是 404；发版后后台需重新登录，让新权限码进 token）

### 阶段 33：客服知识库补库 + 生产同步 + 评价列表修复（2026-11-05）

> 用户重点要求：「**执行了本地的更改数据库之后，一定要同步到线上。**」本阶段把它固化为流程（规则写入 `findings.md` 顶部「硬规则」）。

- [✓] **知识库扩量**：`docs/QandA.xlsx` 332 → **532 行**（+200 条，覆盖店铺与商品 / 活动与优惠 / 会员制度 / 分类导购 / 品牌导购 / 参数速查 / 现货库存 / 物流配送 / 下单支付 / 售后服务 / 账号与评价 / 客服与兜底），单 Sheet `qa` 四列不变；样式统一（微软雅黑 10、细边框、斑马纹、自动换行、冻结 A2、筛选 A1:D533）；备份 `%TEMP%\QandA-backup-20261105.xlsx`
- [✓] **去重与入库**：按 `(category, question)` 去重 → **519 条唯一**（13 条同分类重复合并）；`docs/sql/20261105_kb_seed.sql` 为 519 条 `INSERT ... ON DUPLICATE KEY UPDATE`（可重复执行）；建表 `docs/sql/20261105_kb.sql`
- [✓] **本地 ↔ 生产双库同步**：两份 SQL 上传 `/www/java/sql/` → 生产执行 → `kb_qa` **519 条、全部 `status=1`** → 重启 `ai-service` 容器
- [✓] **生产验证**（容器内直调）：`你们店主要卖什么？` / `店里现在有什么活动？` / `会员制度是怎么样的？` / `iPhone 15 Pro 多少钱？` / `有什么手机推荐？` 全部 `matched=True`（100~142.9 分）；`你好` `matched=False`（8.0 分，寒暄未入库）
- [✓] **评价列表三处修复**（`e-commerce/src/pages/orders/review/ReviewList.vue`）：内容去标签（只留正文两行截断）、窄屏操作按钮 `flex-wrap` + 操作列 `width:150`、序号补 `index` 解构并按分页连续递增
- [ ] **【待发版】** 后台前端 `release-web.sh`
- [ ] **【待确认】** 旧知识库矛盾条目（积分 / 价保 / 券叠加）修订；寒暄问答 5~8 条补库

### 阶段 34：上传链路安全加固（2026-11-07）

> 起因：`docs/fileupload` 盘出上传链路的 7 个缺口，用户拍板本次修 3 类（魔数校验 + 孤儿清理 + 落盘健壮性），
> 明确不做限流 / 对象存储 / 私有读。完整说明见 `docs/fileupload` 第 6 章。

- [✓] **魔数校验**（新增 `common/utils/MediaMagic.java`）：读文件头比对真实类型，与声明类型不符返回 400。
      接入 `UploadServiceImpl.upload()`（后台图 + C 端图）、`UserServiceImpl.uploadAvatar()`（头像）。
      **不能用 `ImageIO.read()`** —— JDK 17 ImageIO 不支持 webp，本项目统一输出 webp，会把正常图片全判为非法。
      实测 9/9 通过（真 webp/jpg + png/gif/mp4 头 + 伪装脚本 + 空文件 + 两种拒绝场景）
- [✓] **落盘健壮性**：`mkdirs()` 失败且目录仍不存在 → 抛「存储目录不可用」；`transferTo` 后校验文件存在且 `length() > 0`；
      头像补 2MB 体积校验（Service 层原来只校验类型，体积全靠容器层 2MB 兜底）
- [✓] **孤儿文件清理任务**（新增 `product-service/.../OrphanImageCleanupTask.java` + `OrphanImageCleanupTaskRunner`）：
      扫 `upload/`，删掉数据库里已无引用的上传文件。`admin_monitor_task` 动态调度（`task_name=orphanImageCleanup`、
      `task_group=product-service`、每天 04:30）；`monitor-service` 的 `MANUAL_RUN_ALLOWED` 放开手动执行；
      `product-service` yml 开 `monitor.dynamic-task.enabled` + `upload.orphan-cleanup.*`
- [✓] **引用列清单**（`OrphanImageCleanupTask.REFERENCED_SQL`）：一条 UNION ALL 全查 **17 个图片列 + 2 个富文本列**，
      跨 6 个模块的表；一条正则统一提取路径，兼容单值 / JSON 字符串数组 / JSON 对象数组 / 富文本 HTML 四种存储。
      **新增图片类型时必须补一条**，否则新类型的图会被误判成孤儿
- [✓] **四道安全闸**：`dry-run` 默认 true / 静态资源白名单（`swiper/` `share/` `side-bar/` + 4 张根级静态图，
      这些是前端代码写死引用、不在数据库里的）/ 7 天门槛 / 单次 500 个上限
- [✓] **跨平台坑已修**：静态白名单判断**不能用 `Path.parts` 解析 URL**（Windows/Linux 段数下标不同，会让白名单在本地失效），
      改用 `url.split("/")`。实跑验证：4 张根级静态图**未**出现在待删清单里
- [✓] **实测基线**（本地库 + 本地磁盘）：数据库引用路径 460 条、磁盘文件 498 个（不含 opt），真正孤儿 21 个
      （after-sale 13、body 5、upload 2、banner 1），其中 20 个超 7 天；Java 实跑 26 个（含 6 个 opt 派生孤儿），
      与 Python 独立脚本基线逐项对齐
- [✓] **沙箱真删验证**（副本目录，真实 `upload/` 未动，仍是 708 个文件）：708 → 682，删除清单与 dry-run **完全一致**；
      三条边界断言全对 —— 刚上传的新孤儿**保留**（7 天门槛）、100 天前的老孤儿**删除**、100 天前但**被引用**的文件**保留**
- [✓] **单元测试 10/10 通过**（新增 `spring-boot-starter-test` 到 common / product-service 的 test 作用域）：
      `MediaMagicTest` 5 例（5 种格式识别 / 伪装文件拒绝 / 类型不符拒绝）+ `OrphanImageCleanupTaskTest` 5 例
      （opt 与原图同 URL 的路径换算 / 静态资源白名单 / 端到端只删真孤儿 / 演练模式不删）
- [✓] **生产库已同步**：`docs/sql/20261107_orphan_cleanup_task.sql` 同一份在本地与生产均执行并核对
      （生产 id=8，task_group=product-service，cron `0 30 4 * * ?`）
- [ ] **【待发版】** common 改了 → product-service / user-service / monitor-service **都要重发**
- [ ] **【待人工】** 上线后先跑一次 dry-run 演练（后台「系统监控 → 定时任务」手动执行 `orphanImageCleanup`），
      核对清单无静态资源、无被引用文件、无比 7 天内的新文件，再把 `upload.orphan-cleanup.dry-run` 改 false
- [ ] **【本次明确不做】** 上传限流（Redis）/ 本地盘改对象存储（OSS-COS）/ 静态资源私有读签名 —— 理由见 `docs/fileupload` 第 6.2 / 8 章

### 阶段 35：库存管理模块（2026-11-04 ~ 11-07，代码完成）

> 复用一级「库存管理」菜单扩展（原「库存列表」改名「SKU库存」），不新建微服务、并入 product-service，不改 Gateway 路由与 Swagger 配置。方案见 `docs/sotck`。

- [✓] 后端（product-service）：`StockDashboardController`（库存看板）/ `StockSkuController`（SKU 库存）/ `StockTransactionController`（库存流水）/ `StockOrderController`（入库/出库/调整单）/ `StockInternalController`（跨服务出入库唯一入口，校验 `X-Internal-Call` 头）
- [✓] 库存预警阈值：`ALTER TABLE product_sku ADD min_stock INT`（不引入关联表）
- [✓] 库存变动必须生成单据 + 流水 + 操作日志，可追溯；单据取消自动反向冲销且同一事务；禁止无单据直接改库存
- [✓] 跨服务最终一致性：order-service / aftersale-service 经 `STOCK_INTERNAL_URL` 调 product-service，失败订单标「库存异常待处理」人工补录；不引入 Seata
- [✓] 旧 product 级库存接口 `AdminStockController` 已删除（不留兼容）；商品更新接口禁传 `stock`，商品编辑页不再直接改 SKU 库存
- [✓] 前端（`e-commerce/src/pages/stock/`）：Dashboard / StockList / Transactions / Receipts / Shipments / Adjustments + 三个新建单据弹窗（出库单新建为后补，权限码单独配置）+ `StockOrderPrint.vue`（`window.print()` 打印可另存 PDF，打印 CSS 静态引入）
- [✓] 交互规范：SKU 库存「编辑库存」= 操作列独立按钮 + 弹窗（禁止行内编辑/隐藏入口）；表格不限列宽、禁用 `:ellipsis="true"`（会强制 `table-layout:fixed` 导致布局异常）
- [✓] 权限分配（2026-11-07，本地 + 生产已同步，已登录账号需重登生效）：超管/仓管全量；运营 = 看板/SKU库存/流水/入库单/出库单（无调整单）；销售 = 看板/SKU库存/流水/出库单只读；客服无
- [ ] 待清理：历史悬空权限码 `order:order:detail`、`order:order:export`；销售规格库复用商品权限码是否独立待决策

### 阶段 36：系统监控模块（2026-11-05 一期 + 其后二期动态定时任务）

> 方案见 `docs/system-monitor-plan.md`。一期复用现有表 + Redis，无新建表，菜单（admin_menu/admin_role_menu 8 条）本地 + 生产已同步。

- [✓] monitor-service（9008）：`ServerMonitorController` / `CacheMonitorController` / `OnlineUserController` / `TaskMonitorController`；网关路由 `/api/admin/monitor/**` 独立成段置于 `/api/**` 之前
- [✓] 采集方式：Nacos 服务发现直连各服务 Actuator 端点，30 秒缓存（服务重启后旧 DOWN 状态 30 秒内自愈）；order-service 的 RabbitMQ 健康检查已禁用（演示场景无依赖，避免误报）
- [✓] 二期动态定时任务：`admin_monitor_task` 表 + `DynamicTaskScheduler` / `MonitoredTask` / `TaskMonitorService`，每 30 秒自动重载、改调度不重启；支持 cron / 固定延迟 / 固定频率 + 并发控制；现有 logCleanup、closeExpiredOrders、unpaidNotify、autoConfirmOrders、orphanImageCleanup 等任务
- [✓] 前端（`e-commerce/src/pages/monitor/`）：server / cache / online / task 四子页；表格不限列宽不截断（运维排障必须看全）；轮询 10~15 秒 + 标签页后台停轮询

### 阶段 37：财务模块（2026-11-05 ~ 11-06，代码完成）

> 方案见 `docs/financial` 与 `../finance-design.md`。流程从简适配演示：审核为终态操作，不做反审核。

- [✓] 后端（order-service `AdminFinanceController`，`/api/admin/finance`）：收款记录列表/汇总/导出/详情/审核/备注 + 收款账户列表/启用列表/新增
- [✓] 前端（`e-commerce/src/pages/finance/`）：receipt（收款记录）/ account（收款账户）

### 阶段 38：规格库 + 商品 SKU 多规格（2026-11-06，代码完成）

> 方案见 `docs/sku`。

- [✓] 后端（product-service `AdminSpecController`）：规格库按分类 ID 向上递归祖先链，返回「当前分类 + 祖先分类 + 全局通用」规格，禁止返回全量导致品类混杂；规格库数据发布前执行分类归属修复 SQL；网关已补 `/api/admin/specs/**`（漏配 404）
- [✓] 后台添加商品改两步向导：第一步分类/基础信息/详情，第二步商品参数 + 销售规格（单卡片整合、无冗余提示文案）
- [✓] 规格维度平铺 chip 点选（禁用下拉/手输标签），选中自动展开候选值并默认全勾；SKU 组合表格常驻显示；批量填充仅为辅助，价格以手动填写为准；多规格商品的价格/市场价/成本/库存只在 SKU 表格填，移除商品级独立价格框
- [✓] 商城 H5 / 小程序规格选择统一：每维度单选平铺、红色边框选中、无库存置灰、未选全价格显示「XX 起」；SKU 逻辑抽 `utils/sku.js`（小程序版已迁入分包 `pages/order/utils/sku.js`，规避微信主包「未使用 JS 文件」校验——主包 tree-shaking 不保证 side-effect import 生效，分包目录不受该校验约束）

### 阶段 39：AI 能力设计定稿（2026-11-07 定稿，2026-11-08 落地见阶段 41）

> 全文见 `docs/ai`（38KB）。覆盖电商后台 AI 客服 + 商城 H5 AI 客服；小程序客服维持纯知识库检索不接大模型。

- [✓] 按端分工：商城 H5 面向客户接大模型（商品推荐/订单售后查询/客服问答）；后台面向运营（角色配置/工具管理/知识库/文案生成回填），客户侧能力禁止堆入后台
- [✓] 架构：两端共用一套 LangGraph 编排（工具集/知识库/系统提示词区分），请求统一经 Java 网关 JWT 鉴权后转发 Python `ecommerce-python`（原 `recommender-python` 已改名，引用已同步）
- [✓] 表设计：统一 `ai_` 前缀 + `created_at/updated_at` + utf8mb4_general_ci；`ai_chat_role`（系统提示词存 `system_message`，不进 ai_tool）/ `ai_chat_conversation` / `ai_chat_message`（保留 admin_id + user_id，`source` 区分来源）/ `ai_tool` / `ai_kb_dataset` + `ai_kb_segment`（两表知识库，角色绑定到库）/ `ai_kb_qa` / `ai_kb_miss`（原 kb_qa/kb_miss 已改名，代码/SQL/文档同步）
- [✓] 工具 = Function Calling：`ai_tool` 表只存元数据/开关/权限，执行逻辑由实现 `AiToolHandler` 接口的 Java 类完成；权限两层交集（用户角色 ∩ 角色绑定工具），权限码 `ai:tool:xxx`；好物推荐工具查真实库返回商品卡片（禁大模型编价格/库存），后台卡片带「查看商品/复制链接」
- [✓] RAG：硅基流动 `BAAI/bge-m3` 向量模型，Rerank 为 P1 可选；商城内置「商城客服」角色固定绑定 7 个客户工具 + `ai_kb_qa` 知识库；商城端保留会话历史列表
- [✓] 已落地并上线（2026-11-08，阶段 41）：建表 → Java 网关鉴权转发 → Python LangGraph 编排 → 后台 2 页（AI 对话/商品文案助手；角色/工具/知识库/聊天管理 4 个菜单已入权限库但页面未开发）+ 商城客服页

### 阶段 40：国家补贴 + 商城移动端适配收尾（2026-11-07，代码完成，未提交/未发版）

- [✓] 国家补贴复用优惠券体系（用户拍板：不建独立补贴表）：补贴 = 10% 折扣券，结算页手动勾选、不自动抵扣；后端 `GET /api/coupons/product/{productId}/subsidy`（order-service，**可选登录**——`JwtInterceptor` 放行无 token）+ `ProductSubsidyVO`，领取/结算复用现有券逻辑；商城详情页补贴横幅改数据驱动按状态显示按钮
- [✓] 移动端适配（`docs/mall-mobile-adapt.md`）：首页窄屏只保留一级分类图标行（移除二级分类平铺）；详情页窄屏底部固定购买栏（`env(safe-area-inset-bottom)` 避开系统导航栏），窄屏隐藏页内购买按钮、宽屏隐藏固定栏；`MobileTabBar` 安全区适配
- [✓] 修复详情页 el-rate 评分全灰：Element Plus 2.14.x 的 el-rate 仅支持 `model-value`（无 `value`/`active-color`），误用 `value` 导致始终 0 分；改用 `model-value` + 显式 `disabled-void-color`
- [✓] 恢复离线脚本 `rebuild_opt_images.py`（从 git 历史 2c052d4 前恢复到暂存）：仅补商品列表 400×400 opt 缩略图（不存在或 >30KB 才重压），不改原图不改库
- [ ] 待提交 git + 发版：order-service 镜像 + `release-mall.sh`

### 阶段 41：AI 客服模块落地（2026-11-08，已上线）

> 方案见 `docs/ai`；实际实现偏差与踩坑见 `findings.md`「AI 客服模块落地」。

- [✓] **建表与种子**（`docs/sql/20261108_ai_module.sql`，**本地 + 生产同一份均已执行**）：9 张 `ai_` 表（`ai_tool` / `ai_chat_role` / `ai_chat_role_tool` / `ai_chat_role_knowledge` / `ai_chat_conversation` / `ai_chat_message` / `ai_call_log` / `ai_kb_dataset` / `ai_kb_segment`）+ 菜单 1000（目录）、1001~1006（AI 对话/商品文案助手/聊天角色/工具管理/知识库管理/聊天管理）、按钮 1010~1017 + 9 个工具 + 2 个内置角色（商城客服绑 7 个、运营助手绑 9 个，共 16 条绑定）；角色授权超管/运营全量、客服仅 AI 对话
- [✓] **角色提示词修正**（`docs/sql/20261108_ai_role2_prompt_update.sql`，双库已执行）：运营助手必须调 `update_product` 才能改商品文案
- [✓] **Python 编排**（`ecommerce-python/app/ai/`）：LangGraph `StateGraph(intent → decide ⇄ tools)`；`intent.py` 关键词前置路由 `chat/danger/llm`（`danger` = 修改动词 + 字段名）；`tools.py` 9 个工具，危险工具 `update_product` 只产出 pending action、前端二次确认后 `execute_pending_action`；`app/api/ai_chat.py` 提供 `/api/mall/ai/**`、`/api/admin/ai/**` SSE 会话接口 + 文案生成
- [✓] **Java 内部数据接口**：`common` 新增 `InternalApiInterceptor` + `WebMvcConfig` 注册，`/internal/ai/**` 校验 `X-Internal-Token`；product / order / aftersale 各一个 `InternalAiController`
- [✓] **网关**：`/api/mall/ai/**`、`/api/admin/ai/**` 转发 `AI_SERVICE_URL`；`/internal/ai/product|stock|orders|aftersale` 三条独立路由转发到对应 Java 服务（放在 `/api/**` 通配之前，否则被吞）
- [✓] **前端**：商城 `views/ai/AiChat.vue`（`/ai-chat`，会话历史 + 商品卡片，需登录）；后台 `pages/ai/AiChat.vue`（角色选择 + 流式对话）+ `CopyAssistant.vue`（文案生成）+ `api/ai.js` + `/ai` 一级路由
- [✓] **生产部署**：镜像 `20261108-ai1`（gateway / product-service / order-service / aftersale-service / ai-service，5 个容器 running），ai-service 追加修复后再发 `20261108-ai2` 并重新生成 override；服务器 `/www/java/.env` 补齐 `OPENAI_BASE_URL` / `CHAT_MODEL` / `AI_INTERNAL_TOKEN`（48 位随机）/ `AI_INTERNAL_BASE_URL=http://gateway:8080`
- [✓] **生产验证**：`/internal/ai/**` 无 token → 401，带 token 搜索商品 → 200；`ai_` 表 11 张（9 新建 + `ai_kb_qa` / `ai_kb_miss`）、9 工具 / 2 角色 / 16 绑定 / 519 条知识；真实用户端到端流式对话正常，`ai_chat_conversation` / `ai_chat_message` / `ai_call_log` 均落库
- [✓] **intent 强约束**（用户要求）：`app/ai/agent.py` 新增 `_TOOL_RULES` 追加在 system message 之后（提到「我的订单/我的XX/发货了吗」必须先 `query_order`、售后先 `query_aftersale`、库存先 `query_stock`、商品详情先 `query_product/search_product`，禁止未调工具编造业务数据，空结果如实告知），修复「我的最新订单发货了吗」被当通用发货政策走 `search_knowledge`；会话表仍存原始提示词，不写规则
- [✓] **AI 头像**：源图 `upload/image/zongjie.webp` → 两个前端 `src/assets/ai-avatar.webp`；商城欢迎区 56px + 每条 AI 消息 32px、后台 AI 消息 32px，圆形 `object-fit: cover`（原 icon 样式的 `font-size` / 主色背景必须去掉）
- [ ] **待发版**：后台前端（`release-web.sh`）与 PC 商城（`release-mall.sh`）—— 本次只发后端镜像，两个 AI 页面与头像尚未对外可见
- [ ] **待开发**：后台「聊天角色 / 工具管理 / 知识库管理 / 聊天管理」4 个页面（菜单与权限已在库中；后台侧边栏按静态路由渲染，当前不产生死链）

### 阶段 42：数据库监控模块（2026-11-10，代码完成，DB 双库已同步，待发版）

> 方案见 `docs/database/database-monitor-plan.md`；实现口径与踩坑见 `findings.md`「数据库监控模块」。

- [✓] **建表 + 菜单 + 任务注册**（`docs/sql/20261110_db_monitor.sql`，**本地 + 生产同一份均已执行并核对**）：表 `admin_db_slow_sql`（4 个索引，只存归一化 SQL 与 MD5 指纹，不存参数值）+ 菜单 921（数据库监控，`monitor:db:list`）/ 按钮 922（清空历史，`monitor:db:clear`）+ 超管角色授权 + `admin_monitor_task` 注册 `dbSlowSqlCleanup`。92x 段执行前已确认空闲（908~910 为定时任务按钮、911 缓存 Key、912~920 财务、1000+ AI）
- [✓] **采集端 `common`**：`ecommerce/monitor/sql/` —— `SqlMonitorInterceptor` 拦 `Executor.query` 两个重载 + `update`，`nanoTime` 计时只记 ≥300ms；SQL 归一化（压空白 → 字符串/数字字面量换 `?` → `IN (…)` 收敛）+ MD5；`SqlMonitorRecorder` 每分钟 60 条限流 + 独立线程池（1/2/队列 1000）+ 自定义拒绝策略（丢弃并计数），JdbcTemplate 直写、异常只 warn；`SqlMonitorConfig` 条件装配（MyBatis 存在 + `monitor.sql.enabled`），common 的 mybatis starter 设 `optional`
- [✓] **查询端 `system-service`**：`DatabaseMonitorController`（`/api/admin/monitor/database`）—— overview / slow-sqls 分页 / top / trend / tables / pools / DELETE 清空；`DatabaseMonitorService` 查 `SHOW GLOBAL STATUS`、`information_schema`，QPS 用 Redis 采样差值；`ServiceMetricsService` 增加 Hikari 指标（单独 try，失败降级不影响 JVM/HTTP 指标）
- [✓] **清理任务**：`DatabaseSlowSqlCleanupTask` + `DatabaseSlowSqlCleanupTaskRunner`（`dbSlowSqlCleanup`，每天 03:20，每批 1000 / 最多 200 批），三处任务名一致，并加入 `TaskMonitorService.MANUAL_RUN_ALLOWED`
- [✓] **前端**：`e-commerce/src/pages/monitor/database/DatabaseMonitor.vue` + `api/monitor.js` 7 个方法 + `/monitor/database` 路由（`meta.permission='monitor:db:list'`，清空按钮 `v-permission`）
- [✓] **验证**：后端 `mvn compile`、前端 `npm run build`；临时实例（19008/19009，禁 Nacos 注册）7 个接口实测通过，阈值调 0 后真实抓到慢 SQL 记录，任务可见且可手动执行；测试数据已清理
- [ ] **待发版**：重发所有引 common 且有 MyBatis 的服务（auth / product / order / aftersale / user / system）+ 后台前端；上线后确认 user-service（MyBatis-Plus）能否采集

## 订单状态合并：退役「待确认收款(2)」（2026-11-04）

- [✓] **判断**：code=2 是原 Node 真实支付场景的「商家人工确认到账」。本项目支付是 mock 的，点一下即成功，
      不存在到账延迟/对账，这一环纯属人工卡点——**生产库积压 63 单**卡在 2，商家不逐单点「确认通过」
      订单就永远进不了发货。（注：库存是**下单那一刻**扣的，与支付无关，「付了钱所以库存够」不是该状态的由来。）
- [✓] **状态码保留不动**：1待付款 / 3待发货 / 4已发货 / 5已完成 / 6已取消，**2 号空出来不再使用**
      （不动 4/5/6，报表 SQL `order_status <> 6` 不受影响）
- [✓] **后端**：`OrderStatusEnum` 删 `PENDING_CONFIRM`，状态机改 `PENDING_PAY → PENDING_SHIP → SHIPPED → FINISHED`；
      `payOrder` 直接置 3；删 `PUT /api/orders/{id}/confirm` 与 `PUT /api/admin/orders/{id}/confirm`；
      取消订单的「已支付未发货」判断去掉 2；`AfterSaleServiceImpl` 仅退款可申请状态改 3/4
- [✓] **关键坑**：`orders.pay_at` 原先只在 status=2 写入，合并后改到 **status=3** 写入
      ——漏改会让营业额统计（按 `pay_at`）归零
- [✓] **后台前端**：订单管理页 Tab 补齐为 全部/待付款(1)/待发货(3)/已发货(4)/已完成(5)/已取消(6)；
      新增 `PendingShipOrders.vue`（去掉「确认通过/驳回」审核弹窗，改 查看/取消/删除）；
      发货页「N 单待确认收款」提示删除；工作台「待确认订单」待办删除、`PAID_STATUS` 改 `[3,4,5]`
- [✓] **小程序**：订单列表 Tab「待发货」改 `statuses:[3]`、订单详情跳转/售后可申请状态去掉 2
- [✓] **PC 商城**：删 `confirmOrder` API；**「确认收货」原先误调 `/confirm`（那是确认收款接口）→ 改调 `/finish`**
      （原来在 status=4 点「确认收货」会报 400「不允许从「已发货」变更为「待发货」」）；售后可申请状态去掉 2
- [✓] **数据库**：本地库 + 生产库执行同一份 `docs/sql/order-status-merge-pending-confirm.sql（该脚本已从 docs/sql 删除，见 git 历史）`
      （存量 2→3、`confirm_at` 用 `pay_at` 回填、字段注释同步）；生产执行前已备份
      （`/www/backup/e_commerce_db_2026-11-04_0929.sql`）
- [✓] **`orders.confirm_at` 删列**（2026-11-04，用户自行在本地库 + 生产库执行 `docs/sql/drop-confirm-at.sql`）：
  该列原是「商家确认收款时间」，状态 2 退役后失去语义，合并时曾临时复用为「进入待发货的时间」，现直接删掉。
  代码侧同步清理完毕：`Order` 实体 / `OrderVO` 删字段、`OrderMapper.xml` 5 处 SELECT 与 `updateStatus`、
  后台日期筛选白名单去掉 `confirm_at`（`pay_at` 已在白名单）、快递发货列表「收款确认时间」列改「支付时间」`payAt`、
  PC 商城订单详情删掉错位的「确认收货」行。
  **执行顺序提醒**：必须先发新代码再删列 —— 旧代码的 mapper 还 SELECT `confirm_at`，列先删会 500。
- [✓] **小程序「用户协议」「隐私政策」内容**（2026-11-04）：两个入口（`pages/my/about-us.vue` → `/pages/article/detail?title=用户协议`
  / `?title=隐私政策`）此前指向空库（`promotion_article` 0 篇），点进去只显示「内容不存在或已下架」。
  正文写在 `docs/legal/user-agreement.html`、`docs/legal/privacy-policy.html`（富文本 HTML），
  `docs/sql/seed-legal-articles.sql` 灌进文章表（新建分类「协议与公告」+ 两篇文章，status=1 上架）。
  后端按**标题精确匹配**取文（`ArticleMapper.findEnabledByTitle`：title 精确 + status=1 + is_deleted=0），
  所以标题必须严格是「用户协议」「隐私政策」，改标题会取不到。
  待办：正文里的【运营主体全称】【联系电话】【联系邮箱】需运营方填真实信息；
  微信后台「用户隐私保护指引」已配置（收集手机号、收货地址、openid 等）、主体名称已与营业执照对齐。
- [✓] **修「文章不存在或已下架」**（2026-11-04）：小程序取文章的请求是**双重编码** ——
  关于页跳转时 `encodeURIComponent` 过一次，微信**不会自动解码 onLoad 的 query**，
  `pages/article/detail.vue` 拿到编码态后又交给 `api/article.js` 再编码一次 → net 上成了
  `%25E9%259A%2590...`，后端按标题精确匹配必然查不到。
  修法：`detail.vue` 的 `onLoad` 里先 `decodeURIComponent` 一次（加 try/catch 兜底），api 层再编码一次 → 单次编码。
- [✓] **小程序补「注销账号」入口**：后端 `POST /api/user/deactivate` 早就实现了，但小程序没有入口，
  而协议里承诺了「我的 - 个人资料 - 注销账号」—— 不能承诺不存在的功能。
  已在 `api/login.js` 加 `deactivateAccount()`、`pages/my/personal-info.vue` 加「注销账号」卡片
  （二次确认 + 调接口 + 清登录态 + switchTab 回「我的」）；协议里的路径描述同步改成「我的 - 个人资料 - 注销账号」。
  后端已处理注销后再登录：查 openid 命中且 `is_deleted=1` → 403「账号已注销」。
- [ ] **【待发版】** order-service / aftersale-service 镜像 + 后台前端 `release-web.sh` + PC 商城 `release-mall.sh` + 小程序重新上传

## 后台「查看物流轨迹」入口（2026-11-04）

- [✓] **背景**：后台一直没地方看物流轨迹——`GET /api/admin/orders/{id}/logistics` 接口和前端 `api/orders.js` 的
      `getLogistics` 都在，但**全项目没有任何页面调用**，是个死接口；「查看流水」看的是 `order_delivery_log`
      （发货操作流水：发货/补发/改单号 + 操作人 + 时间），**不是物流轨迹**，两回事。
- [✓] **不接真实物流 API**（与「不接真实支付」同理：商品和支付都是假的，物流轨迹保持假数据）：
      快递公司 + 快递单号是发货时写进 `orders` 的**真实值**；轨迹节点（已揽件/运输中/派送中/已签收）是
      后端按订单 id 当种子编的，**按「已经发生的时间」逐条出现**——刚发货只有 1 条，发货越久条数越多
      （C 端接口已发货最多 4 条、后台接口 5 条，已完成再多派送/签收 2 条）；未发货订单返回空数组。
- [✓] **实现**：新增 `src/pages/orders/LogisticsDrawer.vue`（抽屉 + 时间线 + 快递公司/单号，无轨迹显示空态）；
      入口加在**订单详情弹窗**（收货人那行「编辑」旁）和**快递发货列表**操作列（「查看流水」旁）；
      后端零改动
- [✓] **验证**：接口返回 200，`courier`/`trackingNumber`/`traces[{time,status,desc}]` 字段与后端 `LogisticsVO` 对齐；
      前端 `vite build` 通过
- [✓] **轨迹算法重做（2026-11-04，用户指出「过了一段时间就不更新了，一查看还是两天前的状态」）**：
  节点时间原来是**固定偏移**（`shipTime + 2h/6h/18h`），发货一段时间后就永远停在那个节点不再更新。
  改成**滚动时间轴**：已揽件固定在真实发货时间，其余节点按「距现在多久」生成（now-6h / now-6h / now-4h，
  已完成再加 派送中 now-2h、已签收 finishAt），每次查看都是最新状态。
  顺带把轨迹生成抽成 `order-service/support/LogisticsBuilder`，**C 端与后台共用一份** —— 历史上两边各写一份，
  后台那份还漏了「只显示已发生节点」的过滤（刚发货能看到未来轨迹），抽成共用后不会再分叉。
  实测：已发货 4 条、已完成 6 条；间隔 3 秒再取，滚动节点时间随 now 更新、已揽件时间不变（数据已还原）
- [✓] **三端物流轨迹同步（2026-11-04，用户要求「商家看的已送达，客户不能还显示派送中」）**：
  `LogisticsBuilder` 改成 Spring Bean，C 端 `OrderServiceImpl` 与后台 `AdminOrderServiceImpl` 注入同一个实例；
  小程序与 PC 商城本来就走同一个 C 端接口，因此三端内容天然一致。
  快递公司名也统一：新增 `LogisticsBuilder.courierName(code)`（字典表 `delivery_express` 优先 → 枚举 → 原 code），
  C 端订单列表的快递公司名原先读枚举、后台读字典，商家改字典名会出现两端显示不一致，现已统一。
  **时间基准取整点**（`now.truncatedTo(HOURS)`）：同一小时内所有请求返回完全相同的时间戳 ——
  否则两次请求跨一秒时间戳就差一秒，三端就对不上；跨整点自动推进一次。
  实测（后台 admin token vs C 端 token，同一订单）：已发货 4 条、已完成 6 条，
  快递公司 / 单号 / 条数 / 每条状态与文案 / 时间戳 **全部一致**（数据已还原）

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
| 9 | **本地改了库忘记同步生产**（2026-11-05 已真实发生：生产 `kb_qa` 为 0） | 线上功能看着"正常返回"，实际是空库/旧数据兜底，排查时极易误判为代码问题 | 按 `findings.md` 顶部「硬规则」执行：本地跑完立刻在生产跑同一份幂等 SQL + 核对行数；缓存服务要重启或等 TTL |
| 10 | 跨服务调用的目标地址靠环境变量注入，新增变量忘同步服务器 compose（2026-11-07 已发生：生产缺 `STOCK_INTERNAL_URL` 致下单 500） | 容器内默认 `localhost` 指向自身 → Connection refused，C 端主链路直接断 | 新增 `*_URL`/密钥类环境变量必须同步 ① 本地 yml 默认值 ② `deploy/docker-compose.yml` ③ 服务器 compose，`--force-recreate` 重建相关容器并容器内实测 |
| 11 | ai-service 用 compose 的 `env_file: .env` 注入全部配置（`OPENAI_BASE_URL` / `CHAT_MODEL` / `SILICONFLOW_API_KEY` / `AI_INTERNAL_TOKEN` / `AI_INTERNAL_BASE_URL`） | 漏配 → ai-service 起不来或 Python 调 Java 内部接口全 401，商城 AI 客服整体不可用 | 三处同步：① 本地 `ecommerce-python/.env` ② `deploy/docker-compose.yml` ③ 服务器 `/www/java/.env`；改完 `--force-recreate ai-service` 并容器内实跑一次对话 |

## 历史错误

已解决的历史错误不再堆在这里，见 `findings.md` 对应日期章节（CORS 重复、MySQL 3306/3307、Nacos 起不来等）。
