# 进度日志

> 历史会话流水账已精简，只留「当前进度」快照。详细踩坑 / 决策见 `findings.md` 与 `task_plan.md`。

## 当前进度（五问重启检查）

- **我在哪里？**
  2026-10-23 意见反馈（user-service `feedback` 表 + `POST /api/feedback`，类型/内容/截图/联系方式校验，截图落 `upload/image/feedback/`；小程序反馈页接真实提交）+ 换货子表修复（`AfterSaleExchangeReturnMapper.xml` namespace 换行导致换货详情/审核 500；创建换货单补存 return_way/return_time_slot）+ 全局 LocalDateTime 格式化（common `JacksonConfig`，所有接口时间不再带 ISO 的 `T`）。此前 2026-10-23 C 端商品收藏（product-service `product_favorites` + `/api/favorites` 列表/check/收藏/取消，同库 join products、唯一键幂等、下架商品保留并打标；网关补 `/api/favorites/**`；小程序详情页假收藏换真接口 + `pages/my/favorites` 收藏页，「我的」入口接通）。此前 2026-10-23 营业额报表重构（order-service：`resolvePeriod` 返回 `PeriodRange(begin,end,timeDesc)` 日期闭区间、`getDateList` 统一生成连续日期、`turnoverOf` 改收 `LocalDate`，卡片/折线共用一份 period 解析；按天 SQL 修掉 `GROUP BY pay_at` 按秒分组导致折线每天只剩一笔的 bug，补 `shop_id`/`is_deleted` 过滤与 `@Param` 参数名）。此前 2026-10-22 后台营业额统计（order-service `ReportController`：卡片 `/api/admin/report/turnover` + 折线 `/turnover-report`，口径实收 − 退款、退款跟原订单 `pay_at`）+ 取消订单自动生成退款售后单（用户/后台取消已支付未发货订单时，同事务直写售后三表，`reason=cancel`）；网关已补 `/api/admin/report/**` 路由；后台工作台营业额面板已接（昨日/近7天/本月，昨日用前日 vs 昨日双柱，替换原 MonthlySales；纵轴按 2000 的倍数自适应，横轴防裁切）；四张统计卡改走后端 `/api/admin/report/dashboard` + `users/statistics.totalUsers`；来单 WebSocket 收到推送后自动刷新工作台（心跳/重连/语音解锁已加固，**刷新页面后点一下页面才会出声**）。此前 2026-10-21 已完成商品评价、售后增强、售后三类型申请/详情页、售后列表 Tab 与角标。剩余：参数/促销、购物车 2 缺口、退货入库单（仓库核验）、file-service(CDN)、沙箱过期回收/templateGuard、端到端验证。

- **我要去哪里？**
  1. 促销/参数表（business_onsale + parameters）
  2. 补购物车 2 缺口
  3. file-service（CDN/通用文件，图片上传已放 product-service）
  4. 沙箱过期回收 + templateGuard
  5. 端到端验证全链路
  6. 阶段 11 WebSocket 剩余：Redis pub/sub 跨实例广播 + 发货/新售后事件补齐

- **目标是什么？**
  拆成真·微服务部署，跑通 Nacos 注册 + Gateway 路由，再逐个补业务服务。

- **我学到了什么？**（详见 findings.md）
  - 静态资源地址统一拼，别各拼各的
  - `lb://` 长连接与 ThreadLocal 在 WS 下失效，跨实例推送走 Redis pub/sub
  - 订单时间用 Java `LocalDateTime.now()`，别用 MySQL `NOW()`（时区差 8h）
  - `GROUP BY` 大小写不敏感会合并枚举值，查 pay_type 用 `BINARY` 区分
  - 凭据统一走环境变量（缺了就启动失败），别散落在 gitignore 的 dev.yml 里

- **我做了什么？**
  - 阶段 0–9：8 服务骨架、auth 登录、商品域迁移、AI Agent、推荐服务、Redis 分类缓存、订单域迁移+购物车
  - 阶段 0–9（续）：订单功能、用户软删+注销、地址模块、下单幂等、小程序下单链路
  - 阶段 AC：订单软删接口 + 订单时间时区修复
  - 售后域数据迁移 + C 端售后接口（用户归属隔离）
  - 订单/商品关键词搜索、支付方式枚举 + 状态双文案、前端订单列表 6 Tab + 待发货详情 + 商品搜索页 + 地址管理页
  - 2026-10-17：订单物流轨迹（假数据）+ 时区修复收尾 + 订单状态文案统一 + payType 大小写统一 + admin 表建好（后台登录）
  - 2026-10-17：删除 6 个 application-dev.yml，凭据统一环境变量（DB/JWT/微信/AI key），swagger/SQL 日志改环境变量开关
  - 2026-10-18：后台管理端登录端到端跑通（排查「两个 MySQL」→ 最终将 Docker 3307 数据迁回本机 3306，MySQL 一律本地 3306、不用 Docker；前端 `e-commerce/.env` baseURL→8080；gateway 补 WebFlux CORS + 去重 GlobalFilter）
  - 2026-10-18：删 user 表冗余字段 idNumber（entity/mapper/VO/service 4 处，DB 列留待手动 DROP）；本地建专用账号 `e_commerce_db`；配 MySQL 自动备份（脚本 + 每晚 22:00 定时任务，保留 7 天）
  - 2026-10-18：微信支付接入立项（task_plan 阶段 12，回调+查单+幂等+金额校验+对账）；WeChatProperties 按需加载（去 @Component、user-service 显式启用、删 7 服务 wechat placeholder、dev.yml 补 WECHAT 凭据）；下单成功移除购物车后端同事务删除（前端 clearCart 兜底保留）
  - 2026-10-19：后台商品/分类/标签 CRUD 打通（Admin*Controller + mapper + e-commerce 前端 goods/category/tag 页对接；商品模块沿用蛇形 `@JsonNaming(SnakeCase)`，其余模块驼峰）；stock 表建 + 30 条数据迁；确立后台 Admin 端对接契约（见 findings「后台 Admin 端对接契约」）
  - 2026-10-19：后台订单管理打通（AdminOrderController 12 接口 + e-commerce 前端 orders 页对接，状态字符串→数字 code、统一驼峰）；OrderStatusEnum 加 color、OrderVO 加 userId、OrderItemVO 加 stock；订单列表后端分页（4 tab 独立请求）+ 发货页全量 + 日期筛选（dateField 白名单）+ LocalDateTime 加 @JsonFormat
  - 2026-10-20：库存预警接口（AdminStockController + StockList 页，路径 /api/admin/stock）；售后后台审核/删除（AdminAfterSaleController，审核=主表状态+商家备注+子表时间戳白名单 STATUS_COLUMN_MAP，删除=主表/明细/子表软删）；订单/售后支付方式 payTypeDesc 中文修复（前端订单 6 表格 payType→payTypeDesc）；售后前端字段蛇形→驼形对齐 + 列表搜索/日期筛选；PageVO 加 page/pageSize 由后端返回、前端从响应读（不写死 pageSize）
  - 2026-10-20：后台来单提醒 WebSocket（order-service /ws/order + AuthHandshakeInterceptor 握手鉴权 + WebSocketSessionRegistry 按 shopId 分组 + 前端 orderNotify.js 自动连/断线重连/语音播报）；多租户沙箱打通（shop-service 开沙箱 POST /api/shops/sandbox：15 张业务表 id+offset 克隆 + 签发沙箱店 admin 令牌；C 端多租户：user 表加 shop_id、下单/支付/购物车/地址按 user 令牌 shopId、来单提醒按 order.shopId 隔离；小程序端 SHOP_ID 常量登录传 shopId）
  - 2026-10-20：商品图上传（product-service `POST /api/admin/upload`，type 分流 upload/image/{upload|body|category|after-sale}，白名单+10MB，前端商品编辑/富文本/售后凭证上传对接）；订单超时自动确认收货（发货 7 天自动 SHIPPED→FINISHED，sign_status 加 auto 与用户签收 signed 区分）；用户增长统计（user-service `GET /api/admin/users/statistics` 近 12 月新增用户）；后台工作台对接（订单字段蛇形→驼峰 + 状态字符串→数字 code，库存预警换用户增长、销售概览换订单统计、近7天换近12月）；沙箱 cloneOrders/cloneAfterSales 的 order_no 加 _s 后缀修复唯一键冲突
  - 2026-10-21：商品评价模块（product_reviews 建表 + order-service ReviewController/Service/Mapper + OrderVO.reviewed + 小程序 review.vue 真实提交 + 评价晒图/视频上传 + 订单卡「已评价」）
  - 2026-10-21：C 端图片/视频上传（product-service `POST /api/upload` 需登录，type=review/after-sale，白名单加 video/mp4，multipart 50MB，网关 `/api/upload` 路由）
  - 2026-10-21：售后增强——单品售后（CreateAfterSaleDTO.orderItemId + OrderItemVO.orderItemId）、货物状态（AfterSaleGoodsStatusEnum + goods_status 列 + goodsStatusMap）、两套退款原因（未发货5/已发货9，AfterSaleReasonEnum 带 shipped 标志）、仅退款状态规则（2/3/4 可仅退款，5 不可）、售后详情物流（courier/courierDesc/trackingNumber + CourierEnum）、按订单查售后（by-order + OrderVO.afterSale）
  - 2026-10-21：小程序售后前端（apply 按状态过滤服务类型、only-refund 申请页、only-refund-detail 详情页时间轴按状态动态）；后台售后详情退款金额改单品之和 + 货物状态展示
  - 2026-10-21：退货退款/换货申请页与详情页（return-refund/exchange 申请页 + return-refund-detail/exchange-detail 详情页 5 节点时间轴 + 时间轴拒绝态红色叉）；退货方式弹窗组件（return-way-popup 官方退货卡片上门取件/自行寄回+时段）；回填物流独立页（return-tracking 自行寄回回填快递公司/单号）；换货发货地址（ship_address 存换货子表 after_sale_exchange_returns）
  - 2026-10-21：售后列表 Tab 对接（订单列表「售后」Tab 展示售后单 + after-sale-card 组件 + 售后详情按类型跳转）；「我的」页订单入口角标真实数量（onShow 拉 listOrders/listAfterSales 按状态回填 badge，>99 显示 99+）
  - 2026-10-22：后台营业额统计（order-service `ReportController`/`ReportService`：`/api/admin/report/turnover` 卡片 + `/turnover-report` 折线；口径实收 `pay_at` 合计 − `refunded` 售后退款、退款跟原订单 `pay_at`、实收退款都含取消订单；折线按天补 0）
  - 2026-10-22：取消订单自动生成退款售后单（用户端 `OrderServiceImpl` + 后台 `AdminOrderServiceImpl` 取消 status 2/3 时同事务直写 `after_sales`/`after_sale_items`/`after_sale_only_refunds`，`only_refund`+`refunded`+`reason=cancel`，已有售后单跳过）
  - 2026-10-22：文档回滚更新——修正商品模块蛇形契约、`/api/admin/shops/current` 路径、springdoc 已替代手写 `docs/openapi.yaml`、WebSocket 阶段 11 改为已实现单实例版
  - 2026-10-22：网关补 `/api/admin/report,/api/admin/report/**` → `lb://order-service` 路由（原先落到兜底 `/api/**` 转 user-service 404）
  - 2026-10-22：后台工作台营业额面板（`TurnoverPanel.vue` + `api/report.js` + `useWorkplaceData.loadTurnover`，维度 昨日/近7天/本月 + 大数字；近7天/本月折线，昨日用「前日 vs 昨日」双柱对比，复用 week 报表按日期取两点）；舍弃 `MonthlySales.vue`（近12月柱状图，前端本地聚合）；后端移除 quarter 维度
  - 2026-10-22：工作台四张统计卡对齐后端——order-service 新增 `/api/admin/report/dashboard`（今日/昨日营业额、今日/昨日订单量、待发货），user-service `/api/admin/users/statistics` 新增 `totalUsers`；前端 `cards` 不再用订单列表本地聚合；来单 WebSocket 推送后工作台自动刷新
  - 2026-10-22：来单提醒可靠性加固——前端 25s 心跳 + 10s 握手超时 + 无限退避重连（3s→30s）+ 页面可见/网络恢复重连 + 重连后对账刷新；后端回 pong、串行发送、失败/半开连接移除
  - 2026-10-22：来单语音播报修复——浏览器自动播放策略导致刷新后无声，首次点击/按键静音预热解锁 + 缓存中文语音 + `cancel()` 后延迟 speak；使用注意：刷新页面后随便点一下后台页面任意位置（菜单/空白处），之后来单才会正常播报
  - 2026-10-22：营业额图坐标调整——纵轴按数据量级自动选 2000 的整数倍，横轴 `grid.right` 加宽 + 首尾留白 + `hideOverlap` 防日期标签裁切/重叠
  - 2026-10-23：营业额报表重构——`ReportServiceImpl` 抽出 `PeriodRange(begin,end,timeDesc)` + `resolvePeriod` + `getDateList`，switch 只算日期；`turnoverOf` 改收 `LocalDate` 内部转 `[begin 0点, end 次日 0点)`；按天 SQL 改 `GROUP BY DATE_FORMAT(pay_at,'%Y-%m-%d')`、补 `shop_id`/`is_deleted`/`@Param`，退款按天单列一条 SQL（跟原订单 `pay_at`）
  - 2026-10-23：C 端商品收藏——后端 product-service 新增 `product_favorites` entity/vo/mapper/service/controller（`/api/favorites` 4 接口：列表/check/收藏/取消），唯一键幂等、取消物理删除、下架商品保留并下发 `status`；网关 product-service 路由补 `/api/favorites/**`；前端新增 `api/favorite.js` + `pages/my/favorites.vue`，商品详情页 Storage 假收藏换成真接口（静默登录 + 防连点），「我的」页入口接通
  - 2026-10-23：全局 Jackson LocalDateTime 格式化——common 新增 `JacksonConfig`（Boot 4/Jackson 3 的 `JsonMapperBuilderCustomizer` + `LocalDateTimeSerializer`），统一 `yyyy-MM-dd HH:mm:ss`；只改序列化，不动反序列化；字段级 `@JsonFormat` 不受影响；解决后台售后等页面时间带 `T`
  - 2026-10-23：换货子表修复——`AfterSaleExchangeReturnMapper.xml` namespace 被换行截断（`AfterSaleExch\nangeReturnMapper`），换货详情/审核调 `findByAfterSaleId` 抛 BindingException 返回 500，namespace 修正为完整一行；创建换货售后时 `insertSubPlaceholder` 补写 `return_way`/`return_time_slot`，`insertPlaceholder` SQL 同步加列（原详情一直显示「退货方式：—」、回填物流被后端拒绝）
  - 2026-10-23：意见反馈——user-service 新增 `Feedback` entity/mapper/dto/service/controller，`POST /api/feedback`（类型 suggestion/product/order/other、内容 5~1000 字、截图最多 6 张且只收 `/image/feedback/`、联系方式选填，userId/shopId 从 UserContext）；`UploadServiceImpl` 增加 `type=feedback` 目录；前端 `pages/my/feedback.vue` 从本地 Storage 改为真实提交（类型 code+label、截图上传预览删除）

## 错误日志（最近）

| 时间 | 错误 | 解决 |
|------|------|------|
| 2026-10-15 | order-service 启动报 `UndeclaredThrowableException`（Nacos 注册失败） | Docker Desktop 没开 → 起 Docker 重启；纯练 WS 可加 `--spring.cloud.nacos.discovery.enabled=false` 跳过注册 |
| 2026-10-18 | 登录报 `admin` 表不存在 | 应用连错库（3306 本机 MySQL80 旧 schema），数据在 3307 Docker MySQL；最终将数据迁回本机 3306，MySQL 统一本地 3306 |
| 2026-10-18 | 前端跨域 CORS 无头 / 头重复 | gateway 是 WebFlux 无 CORS；加 `CorsWebFilter` + 去重 `GlobalFilter` 解决 |
| 2026-10-23 | 营业额折线每天金额偏小（同一天只剩一笔） | 按天 SQL 误用 `GROUP BY pay_at`（按秒分组），同一天多行被 Java `put` 覆盖；改 `GROUP BY DATE_FORMAT(pay_at,'%Y-%m-%d')`，并补 `shop_id`/`is_deleted` 过滤 |
| 2026-10-23 | 报表接口返回 `{"message":"请求不合法"}` | `GlobalExceptionHandler` 兜底分支（Spring `ErrorResponse`）：路由不存在 404 / `period` 缺参 400；排查时先确认 URL 与方法、参数是否齐全 |
| 2026-10-23 | `GET /api/admin/after-sales/{id}` 返回 500（换货审核弹窗打不开） | `AfterSaleExchangeReturnMapper.xml` 的 namespace 中间断行，实际值含空格，XML 语句未绑定到接口 → `Invalid bound statement`；namespace 写回完整一行 |
| 2026-10-23 | 售后时间显示 `2026-10-21T10:44:55`（带 T） | `spring.jackson.date-format` 只作用于 `java.util.Date`，管不到 `LocalDateTime`；在 common 加 `JsonMapperBuilderCustomizer` 注册 `LocalDateTimeSerializer` 全局格式化 |
| 2026-10-23 | 换货详情「退货方式」一直显示 —，回填物流被拒 | 创建换货售后时 `insertSubPlaceholder` 只写 ship_address，`return_way`/`return_time_slot` 未落库；Service 补字段 + `insertPlaceholder` SQL 加两列 |

---

*每个阶段完成后或遇到错误时更新此文件*
