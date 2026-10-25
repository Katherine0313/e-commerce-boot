# 进度日志

> 只保留当前进度快照。历史流水账已清理，详细踩坑/决策见 `findings.md`，路线见 `task_plan.md`。

## 当前进度（五问重启检查）

- **我在哪里？**
  2026-10-24~25 生产部署完成：腾讯云 8G + Docker Compose（Nacos 3.1.1 + 9 个服务）、宝塔 MySQL/Redis、Cloudflare + Nginx（`api.jiemids.com` 小程序 API / `www.jiemids.com` 后台前端）、后台沙箱/图片/来单 WebSocket、小程序发行；部署文件在 `deploy/` 和根目录 `nginx`。微信审核风险和 Nacos/旧 Node 清理见「未完成事项」。
  2026-10-24：小程序全局悬浮 AI 客服（右侧中间悬浮球 + 底部聊天面板，头像 `static/zongjie.webp`；Pinia `ai-chat` 共享会话，注入 29 个页面，专属客服页除外）+ 小程序专属客服（AI 流式优先 + 微信原生客服会话 + 规则失败兜底：`/api/agent/chat/stream` SSE 首字推送、`sessionId` Redis 多轮历史、`scene=customer-service` 固定 prompt；「人工客服」本地拦截）+ 工作台全局时间选择器（昨日/近7天/本月统一控制营业额、订单统计、用户增长、销量排名；订单统计改按小时/天前端聚合，用户统计接口加 `period` 返回时间桶）+ 订单记录卡换销量排名（`/api/admin/report/sales-ranking`，按件数、pay_at 口径、排除待付款/已取消）。生产部署已完成（见上）。
  2026-10-23：意见反馈、C 端商品收藏、换货子表 namespace/退货方式修复、全局 LocalDateTime 格式化、营业额报表重构（`PeriodRange` + 按天 SQL 修正）。
  2026-10-22：营业额统计接口 + 取消订单自动退款 + 工作台统计卡/营业额面板 + WebSocket 心跳重连加固。

- **我要去哪里？**
  1. 微信审核：模拟支付、隐私政策/用户协议、AI/UGC 内容安全、VIP/优惠券占位
  2. 生产安全收尾：安全组关闭 8848/9848/9849/18080/9001~9008，停旧 Node 3000，删网站根 `.env`
  3. 参数/促销表（`parameters` + `business_onsale`，含活动定时下架）
  4. 购物车 2 个缺口 + 商品/订单/售后端到端验证
  5. file-service（CDN/通用文件）
  6. 沙箱过期回收 + `templateGuard` + 扫码进店
  7. 阶段 11 WebSocket：Redis pub/sub 跨实例广播 + 发货/新售后事件
  8. 阶段 12 微信支付接入（回调 + 查单 + 对账）

- **目标是什么？**
  拆成真·微服务部署，跑通 Nacos 注册 + Gateway 路由，再逐个补业务服务。

- **我学到了什么？**（详见 `findings.md`）
  - 静态资源地址统一拼，`X-Forwarded-*` 不可信
  - `lb://` 长连接与 ThreadLocal 在 WS 下失效，跨实例推送走 Redis pub/sub
  - 业务时间用 Java `LocalDateTime.now()`，别用 MySQL `NOW()`（差 8h）
  - `GROUP BY` 大小写不敏感会合并枚举值，查 pay_type 用 `BINARY`
  - 凭据统一走环境变量，缺了启动直接失败
  - 新增网关路由要同步改 `gateway/application.yml`，否则落到兜底服务 404
  - 新增小程序页面要手动加 `<FloatingChat />`，否则新页面没有悬浮 AI 客服（uni-app 无 App 级全局组件）

- **我做了什么？（近期）**
  - 2026-10-24~25：生产部署（腾讯云 8G + Docker Compose + Nacos 3.1.1 + 9 服务、宝塔 MySQL/Redis、Cloudflare + Nginx 双站点、后台沙箱/图片/来单 WS、小程序发行）
  - 2026-10-24：小程序分包 + 图片后端化（主包 12 页，商品/搜索/订单/售后 4 个分包；共享组件移到 components；`static/image` 15M 删除）+ 全局悬浮 AI 客服 + 专属客服页（大模型 SSE 流式 + Redis session + 微信原生客服）+ 分类请求序号/订单支付售后提交防重 + 列表图 lazy-load + 地址/购物车归属校验补齐 + 工作台全局时间选择器 + 销量排名 + 用户统计 `period` 扩展
  - 2026-10-23：意见反馈、商品收藏、换货子表修复、LocalDateTime 全局格式化、营业额报表重构
  - 2026-10-22：营业额统计、取消订单自动退款、工作台统计卡、WS 心跳/重连/语音修复
  - 更早（10-21 及以前）的完成项见 `task_plan.md` 对应阶段，技术结论见 `findings.md`

## 未完成事项（速查）

- [ ] 微信审核：模拟支付、隐私政策/用户协议、AI/UGC 内容安全、VIP/优惠券占位
- [ ] 生产安全：安全组关闭 8848/9848/9849/18080/9001~9008；停旧 Node 3000；删网站根 `.env`；Nacos 控制台保持 127.0.0.1
- [ ] Cloudflare 目前走 AMS 节点，国内慢；正式可改 DNS-only + Let's Encrypt
- [ ] 购物车：加购校验上架状态；5 接口端到端验证
- [ ] 商品：`parameters` 参数表、`business_onsale` 促销
- [ ] 售后：退货入库单（仓库核验）
- [ ] file-service：CDN/通用文件 + `uploads` 表
- [ ] 沙箱：过期回收、`templateGuard`、扫码进店
- [ ] WebSocket：Redis pub/sub 跨实例、发货/新售后事件
- [ ] AI 客服：function calling / RAG（当前 customer-service 场景先跳过）
- [ ] 微信支付接入（阶段 12）
- [ ] 全链路端到端验证
- [ ] `user.id_number` DB 空列待手动 DROP（代码已不引用，不阻塞）

## 错误日志（最近）

| 时间 | 错误 | 解决 |
|------|------|------|
| 2026-10-23 | 营业额折线每天只剩一笔 | 按天 SQL 误用 `GROUP BY pay_at`（按秒分组）；改 `GROUP BY DATE_FORMAT(pay_at,'%Y-%m-%d')`，补 `shop_id`/`is_deleted` 过滤 |
| 2026-10-23 | `GET /api/admin/after-sales/{id}` 500 | `AfterSaleExchangeReturnMapper.xml` namespace 中间断行；写回完整一行 |
| 2026-10-23 | 售后时间带 `T` | `spring.jackson.date-format` 管不到 `LocalDateTime`；common 注册 `JsonMapperBuilderCustomizer` + `LocalDateTimeSerializer` |
| 2026-10-23 | 换货详情退货方式显示「—」 | 创建换货售后未写 `return_way`/`return_time_slot`；Service + SQL 补字段 |

---

*每个阶段完成后或遇到错误时更新此文件*
