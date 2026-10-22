# 进度日志

> 历史会话流水账已精简，只留「当前进度」快照。详细踩坑 / 决策见 `findings.md` 与 `task_plan.md`。

## 当前进度（五问重启检查）

- **我在哪里？**
  2026-10-22 后台营业额统计（order-service `ReportController`：卡片 `/api/admin/report/turnover` + 折线 `/turnover-report`，口径实收 − 退款、退款跟原订单 `pay_at`）+ 取消订单自动生成退款售后单（用户/后台取消已支付未发货订单时，同事务直写售后三表，`reason=cancel`）；网关已补 `/api/admin/report/**` 路由；后台工作台营业额面板已接（昨日/近7天/本月，替换原 MonthlySales）。此前 2026-10-21 已完成商品评价、售后增强、售后三类型申请/详情页、售后列表 Tab 与角标。剩余：参数/促销、购物车 2 缺口、退货入库单（仓库核验）、file-service(CDN)、沙箱过期回收/templateGuard、端到端验证。

- **我要去哪里？**
  1. 促销/参数表（business_onsale + parameters）
  2. 补购物车 2 缺口
  4. file-service（CDN/通用文件，图片上传已放 product-service）
  5. 沙箱过期回收 + templateGuard
  6. 端到端验证全链路
  7. 阶段 11 WebSocket 剩余：Redis pub/sub 跨实例广播 + 发货/新售后事件补齐

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
  - 2026-10-22：工作台四张统计卡对齐后端——order-service 新增 `/api/admin/report/dashboard`（今日/昨日营业额、今日/昨日订单量、待发货），user-service `/api/admin/users/statistics` 新增 `totalUsers`；前端 `cards` 不再用订单列表本地聚合

## 错误日志（最近）

| 时间 | 错误 | 解决 |
|------|------|------|
| 2026-10-15 | order-service 启动报 `UndeclaredThrowableException`（Nacos 注册失败） | Docker Desktop 没开 → 起 Docker 重启；纯练 WS 可加 `--spring.cloud.nacos.discovery.enabled=false` 跳过注册 |
| 2026-10-18 | 登录报 `admin` 表不存在 | 应用连错库（3306 本机 MySQL80 旧 schema），数据在 3307 Docker MySQL；最终将数据迁回本机 3306，MySQL 统一本地 3306 |
| 2026-10-18 | 前端跨域 CORS 无头 / 头重复 | gateway 是 WebFlux 无 CORS；加 `CorsWebFilter` + 去重 `GlobalFilter` 解决 |

---

*每个阶段完成后或遇到错误时更新此文件*
