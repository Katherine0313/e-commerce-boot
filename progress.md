# 进度日志

> 只保留当前进度快照。历史流水账已清理，详细踩坑/决策见 `findings.md`，路线见 `task_plan.md`。

## 当前进度（五问重启检查）

- **我在哪里？**
  2026-10-29 多租户彻底下线、退化为单店并**已完成生产迁移**：删除 shop-service 模块及网关路由/compose/release 项；JWT 不再签 shopId，`UserContext`/各服务 Mapper/XML/Service 全部去掉 `shop_id`；WebSocket 来单提醒改单集合广播；后台前端店铺名改静态配置 `src/utils/shop.js`；小程序登录不再传 SHOP_ID；AI 推荐服务同步去掉 `products.shop_id`，并补上生产 `.env` 的 `MYSQL_HOST=host.docker.internal`（此前 AI 商品查询一直连不上）。生产执行 01 清掉 2 家沙箱店（55/56）数据、按序发布 7 服务（auth 最后）、02 删除 18 表 `shop_id` 与 `shops` 表并重建唯一键；接口与 AI 商品读取验证通过，备份在服务器 `/www/backup/`。同日新增首页热门推荐接口（推荐分类优先、不足随机补）、修复后台新增商品 `sales_mode` 为空 500。另新建 PC 商城 `ecommerce-mall`（Vue3+Vite SPA），已替换原静态企业站上线 https://www.xiaojiecpn.com，Nginx 同源代理 `/api`、`/image`、`/upload` 到网关，首页/列表/详情/搜索接真实数据。后端商品扩容：`products.specs`（有序 JSON 数组）、分类重构为 8 个一级/21 个二级、商品整理为 126 件（保留 50 个现有 + 76 个按型号去重的种子 id 51~126，每叶子 6 件）、新增 `GET /api/products/page` 分页筛选接口；旧 `/api/products` 保持数组兼容。生产已执行 SQL、同步 51~126 唯一占位图并发布 product-service，线上验证 126 商品/分页/分类/specs 正常（生产额外有一个空的「家电」分类，用户自行删除）。
  2026-10-28 沙箱按浏览器复用（`shops.visitor_key` + 前端 UUID + 唯一索引，退出登录/401 不清 key，24h 内复用原店）；admin 用户名改 `admin_{shopId}`、`create_time` 改开店时间；订单金额注释/标签统一；软著三件套（TEST、源程序文档、用户手册）完成；发布切腾讯云 TCR 镜像（`ccr.ccs.tencentyun.com/jiemi-ecommerce`，9 个仓库，`deploy/release.sh` 一键发布/回滚），服务器 compose 已切 `image:` + `--no-build`，线上 9 容器 Up，网关直连与公网接口均 200。同日小程序提审被拒（VIP 入口点击无响应），新增 `utils/features.js` 功能开关，隐藏 VIP/优惠券/积分与用户协议/隐私政策死链，「联系我们」改跳已有客服页；新增 `deploy/release-web.sh`，后台管理端前端一条命令发布（npm build + rsync + 备份/回滚）；一键体验改为手机号验证码鉴权（`/api/admin/shops/sandbox`）；发布脚本修复多服务 tag 牵连（`docker-compose.override.yml` 固定各服务镜像 + `--no-deps` 部署）；面试演示确定走单店模式，登录页移除「一键体验」，恢复账号密码 / 手机号直接登录模板店，沙箱后端保留未使用。
  2026-10-27 运营报表导出改为模板式（黑马风格：Controller 3 行 + Service 读 `template/运营报表模板.xlsx` 写 response，删除旧 `OperationReportExcelWriter`）；同日运营报表统计口径由近 30 天改为本月（模板扩到 31 行，本月未过完时隐藏多余空行）；沙箱治理：user 克隆脱敏（假 openid、name 脱敏、phone/avatar 置 NULL）+ shop-service 每小时回收过期沙箱店 + 沙箱 token 24h 与店铺同寿命、前台到期强制登出。
  2026-10-26 AI 迁移与生产部署完成：删除 Java `ai-agent-service` 与 Spring AI BOM，新增 Python `recommender-python`（FastAPI + LangGraph）导购机器人，生产容器 `ai-service` 已上线；网关 `/api/agent/**` 静态转发（本地默认 `127.0.0.1:8000`，Docker 由 `AI_SERVICE_URL` 覆盖）。第一阶段能力为多轮对话 + 商品问答（直连 MySQL 只读），并加 JWT 登录校验 + 每用户限流 + 并发/超时保护。线上验证：`/api/home` 200、`/api/cart/count` 401、`/api/agent/chat` 401（鉴权生效）。物流轨迹已过滤未来时间；分类图 webp 已上传并改库，待重建 product/user 容器 + 清 CF 缓存。工作台顶部累计销售额/订单量已改为后端聚合（`/api/admin/report/headline`），近 30 天运营数据报表 Excel 导出已实现（`/api/admin/report/export`），Excel 结构测试和后台前端构建均通过，待部署 order-service + 上传新 dist 验证。
  2026-10-25 线上问题修复完成：小程序审核白屏（上传了 `dist/dev`，接口地址 127.0.0.1 → 改传 `dist/build`）、Redis 改为 Docker 容器 + 分类接口降级（本机 8～12ms）、用户头像 `http→https`（`UrlUtil` 非本机强制 https）、机器人头像本地 webp→jpg；**订单未支付一次性订阅消息代码已完成，2026-10-26 已部署 order/user，生产库 ALTER 待确认**。生产：腾讯云 8G + Docker Compose（Nacos 3.1.1 + Redis 容器 + 8 个 Java 服务 + Python `ai-service`）、宝塔 MySQL、Cloudflare + Nginx（`api.jiemids.com` 小程序 API / `www.jiemids.com` 后台前端）；部署文件在 `deploy/` 和根目录 `nginx`。微信审核风险和 Nacos/旧 Node 清理见「未完成事项」。
  2026-10-24：小程序全局悬浮 AI 客服（右侧中间悬浮球 + 底部聊天面板，头像 `static/zongjie.jpg`；Pinia `ai-chat` 共享会话，注入 29 个页面，专属客服页除外）+ 小程序专属客服（AI 流式优先 + 微信原生客服会话 + 规则失败兜底：`/api/agent/chat/stream` SSE 首字推送、`sessionId` Redis 多轮历史、`scene=customer-service` 固定 prompt；「人工客服」本地拦截）+ 工作台全局时间选择器（昨日/近7天/本月统一控制营业额、订单统计、用户增长、销量排名；订单统计改按小时/天前端聚合，用户统计接口加 `period` 返回时间桶）+ 订单记录卡换销量排名（`/api/admin/report/sales-ranking`，按件数、pay_at 口径、排除待付款/已取消）。生产部署已完成（见上）。
  2026-10-23：意见反馈、C 端商品收藏、换货子表 namespace/退货方式修复、全局 LocalDateTime 格式化、营业额报表重构（`PeriodRange` + 按天 SQL 修正）。
  2026-10-22：营业额统计接口 + 取消订单自动退款 + 工作台统计卡/营业额面板 + WebSocket 心跳重连加固。

- **我要去哪里？**
  1. 后台管理端 `dist` 重新构建上传（sandboxVisitor、订单金额标签）；否则沙箱复用不生效
  2. 订阅消息：生产库 `orders` 两列 + `idx_orders_unpaid_notify` 已确认存在，服务已部署；小程序发行后验证推送
  3. 微信二次提审：`dist/build` 包 + 完整录屏 + request/uploadFile/downloadFile 三个域名
  4. 微信审核：模拟支付、隐私政策/用户协议真实页面、AI/UGC 内容安全（VIP/优惠券占位已用功能开关隐藏，2026-10-28）
  5. 生产安全收尾：安全组关闭 8848/9848/9849/18080/9001~9007，停旧 Node 3000，删网站根 `.env`
  6. 参数/促销表（`parameters` + `business_onsale`，含活动定时下架）
  7. 购物车 2 个缺口 + 商品/订单/售后端到端验证
  8. file-service（CDN/通用文件）
  9. 沙箱 `templateGuard` + 扫码进店（过期回收已完成 2026-10-27）
  10. 阶段 11 WebSocket：RabbitMQ fanout 跨实例广播 + 发货/新售后事件
  11. 阶段 12 微信支付接入（回调 + 查单 + 对账）

- **目标是什么？**
  拆成真·微服务部署，跑通 Nacos 注册 + Gateway 路由，再逐个补业务服务。

- **我学到了什么？**（详见 `findings.md`）
  - 静态资源地址统一拼，`X-Forwarded-*` 不可信
  - `lb://` 长连接与 ThreadLocal 在 WS 下失效，跨实例推送计划走 RabbitMQ fanout
  - 业务时间用 Java `LocalDateTime.now()`，别用 MySQL `NOW()`（差 8h）
  - `GROUP BY` 大小写不敏感会合并枚举值，查 pay_type 用 `BINARY`
  - 凭据统一走环境变量，缺了启动直接失败
  - 新增网关路由要同步改 `gateway/application.yml`，否则落到兜底服务 404
  - 公网慢先对比服务器本机耗时：本机 10～46ms、公网 1～2s，问题在 Cloudflare 跨境，不是 Redis/DB
  - 替换整个 Docker bind mount 目录后容器仍指向旧 inode，挂载它的服务要 `--force-recreate`
  - jar 打进镜像，上传 jar 后必须 `docker compose build` 再 `up -d`，只重启不重建仍是旧代码
  - TCR 发布：compose `image` + `build` 并存时服务器必须 `--no-build`；`docker login` 按用户保存，执行 compose 的 SSH 用户必须登录过仓库
  - Windows 上传 compose 会带 CRLF，和服务器 LF 文件 diff 会整文件变化；用 `diff --strip-trailing-cr` 或 `sed -i 's/\r$//'`
  - 新增小程序页面要手动加 `<FloatingChat />`，否则新页面没有悬浮 AI 客服（uni-app 无 App 级全局组件）
  - 小程序提审必须传 `unpackage/dist/build/mp-weixin`；`dist/dev` 的 BASE_URL 是 `127.0.0.1`，审核必白屏
  - 微信 iOS 对包内本地 webp 解码不稳定，本地图片统一用 jpg/png（`webp="true"` 只对网络图生效）
  - 未开通的功能不要露出入口：只做占位 toast 会被审核判「点击无响应」；统一用功能开关隐藏，上线再打开
  - Spring Cloud Gateway 5.x 默认移除 `X-Forwarded-*`，绝对 URL 的协议不能依赖它；对外 https 场景在 `UrlUtil` 兜底

- **我做了什么？（近期）**
  - 2026-10-29：RabbitMQ 最小 demo（order-service 加 AMQP，dev profile 下启动自动发送/消费 demo.queue，本地 Docker rabbitmq 3.13-management 跑通；管理台 http://127.0.0.1:15672 ，账号 guest / guest）
  - 2026-10-29：PC 商城 `ecommerce-mall` 首版上线 xiaojiecpn.com（首页/列表/详情/搜索，Nginx 同源 `/api` 代理）
  - 2026-10-29：商品扩容后端完成并上线：specs 属性、8×21 分类、126 商品（按型号去重）、`/api/products/page` 分页筛选；生产 SQL/图片/product-service 已发布验证
  - 2026-10-29：首页热门推荐接口（`/api/products/hot`：categories.recommend 优先 + 随机补足）；后台新增商品 sales_mode 空值 500 修复；生产单店化迁移完成
  - 2026-10-29：多租户彻底下线（删 shop-service / JWT shopId / 18 表 shop_id / 沙箱前端与 SHOP_ID / AI 服务 shop_id），新增两阶段迁移 SQL 与执行说明，`deploy/release.sh` 调整发布顺序（auth 最后）
  - 2026-10-28：沙箱复用（visitor_key + 前端 UUID）、admin 用户名/时间修复、订单金额文案统一、软著材料、TCR 镜像发布（9 仓库 + release.sh，服务器已切 image + --no-build）、小程序提审驳回修复（未开通功能统一用 `utils/features.js` 开关隐藏）、后台前端一键发布 `release-web.sh`
  - 2026-10-27：沙箱过期强制登录（沙箱 token TTL 跟店铺 24h、前端按 `expiresAt` 定时踢人 + 路由守卫校验 JWT `exp`）；运营报表导出改模板式（openpyxl 生成 `template/运营报表模板.xlsx`，预置 30 行样式）+ 沙箱治理（克隆 user 脱敏、过期回收定时任务、19 张表按外键顺序清理）
  - 2026-10-26：AI 迁移到 Python 并上线（FastAPI + LangGraph + JWT/限流 + 并发/超时）、删除 Spring AI 与 Java AI 模块；物流轨迹过滤未来时间；生产全量重建（8 个 Java 服务 + `ai-service`）；分类图 webp 上传与生产库更新；工作台累计销售额/订单量改后端聚合，新增近 30 天运营数据报表 Excel 导出
  - 2026-10-25：小程序首页加载优化（分类/商品/详情图批量压缩到 `upload/image/opt/`，`WebMvcConfig` 优先读 opt、缺失回落原图；首页接口聚合为 `/api/home`；推荐结果 Redis 缓存 60s + Python 超时降至 500ms；登录/购物车角标延后到首屏后 + 本地缓存；新增 `/api/cart/count` 轻量角标接口；分类面板骨架屏）
  - 2026-10-25：线上问题修复（小程序改传 `dist/build`、Redis 容器化 + 分类降级、用户头像 https、机器人头像 jpg）+ 订单未支付一次性订阅消息（提交订单授权、下单后第 20 分钟推送、点击 `pending-pay?orderNo`、access_token Redis 共享）
  - 2026-10-24~25：生产部署（腾讯云 8G + Docker Compose + Nacos 3.1.1 + Redis 容器 + 9 服务、宝塔 MySQL、Cloudflare + Nginx 双站点、后台沙箱/图片/来单 WS、小程序发行）
  - 2026-10-24：小程序分包 + 图片后端化（主包 12 页，商品/搜索/订单/售后 4 个分包；共享组件移到 components；`static/image` 15M 删除）+ 全局悬浮 AI 客服 + 专属客服页（大模型 SSE 流式 + Redis session + 微信原生客服）+ 分类请求序号/订单支付售后提交防重 + 列表图 lazy-load + 地址/购物车归属校验补齐 + 工作台全局时间选择器 + 销量排名 + 用户统计 `period` 扩展
  - 2026-10-23：意见反馈、商品收藏、换货子表修复、LocalDateTime 全局格式化、营业额报表重构
  - 2026-10-22：营业额统计、取消订单自动退款、工作台统计卡、WS 心跳/重连/语音修复
  - 更早（10-21 及以前）的完成项见 `task_plan.md` 对应阶段，技术结论见 `findings.md`

## 未完成事项（速查）

- [ ] 首页优化收尾：`opt/` 图与分类 webp 已上传、生产库 `categories.image` 已是 `.webp`；product/user 容器已于 2026-10-29 重建，还差清 Cloudflare `/image/category/*`
- [ ] 订阅消息：生产库 `orders` 两列 + `idx_orders_unpaid_notify` 已确认存在，服务已部署；小程序发行后验证推送
- [ ] 运营数据导出上线：新版 `order-service` 已随 TCR 部署；待上传新 `e-commerce/dist` + 清缓存 + 验证 xlsx
- [ ] 微信二次提审：`dist/build` 包 + 完整录屏 + request/uploadFile/downloadFile 域名
- [ ] 微信审核：模拟支付、隐私政策/用户协议真实页面、AI/UGC 内容安全（VIP/优惠券/积分/协议死链已于 2026-10-28 隐藏）
- [ ] 生产安全：安全组关闭 8848/9848/9849/18080/9001~9008；停旧 Node 3000；删网站根 `.env`；Nacos 控制台保持 127.0.0.1
- [ ] 公网延迟：本机接口 10～46ms，公网 0.8～2s；Redis 命中率 85%、事件循环 <1ms，瓶颈在 Cloudflare 跨境。待定是否改 DNS-only 直连源站（国内预计 100～300ms）
- [ ] 购物车：加购校验上架状态；5 接口端到端验证
- [ ] 商品：`parameters` 参数表、`business_onsale` 促销
- [ ] 售后：退货入库单（仓库核验）
- [ ] file-service：CDN/通用文件 + `uploads` 表
- [x] 沙箱过期回收 + user 克隆脱敏（2026-10-27，shop-service 每小时清到期店）
- [x] 发布切 TCR 镜像（2026-10-28 起，2026-10-29 起 8 个仓库：7 Java + ai-service；release.sh 一键发布/回滚）
- [x] 沙箱按浏览器复用（2026-10-28，退出登录 24h 内复用原店）
- [x] 多租户沙箱彻底下线（2026-10-29，单店模式）；扫码进店/分享链接不再支持
- [ ] WebSocket：RabbitMQ fanout 跨实例、发货/新售后事件（RabbitMQ 本地 demo 已完成）
- [ ] 小程序重新上传：热门推荐（推荐分类优先）+ 单店化登录（不再传 shopId）
- [ ] PC 商城 P2：后端补 C 端 demo 登录（user-service，dev profile，手机号 13800138000 换 user 令牌），再接购物车/下单
- [x] 商品扩容生产发布（2026-10-29）：SQL + 51~126 图片 + product-service 已上线
- [ ] 商品扩容前端：小程序/后台/PC 接 specs、分页、分类侧边栏
- [ ] 删除生产多余的空「家电 / 电冰箱」分类（用户自行处理）
- [ ] 后台新增商品 500 修复后的 product-service 需确认已发布到生产（代码已修，`sales_mode` 默认 spot）
- [x] ~~个性化推荐/`/recommend`~~：用户决定近期不实现 Python recommend，继续销量兜底（2026-10-29）
- [ ] 导购机器人后续能力：下单、RAG、商品卡片渲染（当前多轮对话 + 商品问答，已上线并加登录/限流）
- [ ] 短信服务商：手机号登录目前仅演示号 13800138000 能收码，待接阿里云/腾讯云短信并关闭 `SMS_TEST_PHONE`
- [ ] 微信支付接入（阶段 12）
- [ ] 全链路端到端验证
- [ ] `user.id_number` DB 空列待手动 DROP（代码已不引用，不阻塞）

## 错误日志（最近）

| 时间 | 错误 | 解决 |
|------|------|------|
| 2026-10-28 | 单服务发布后全量 `docker compose up -d` 报 `No such image ...:<新tag>` | compose 9 个服务共用全局 `TAG`；改为 `docker-compose.override.yml` 固定每服务镜像，部署用 `docker pull` + `up --no-deps` |
| 2026-10-28 | 小程序提审被拒：`VIP` 功能点击无响应 | VIP/优惠券/积分/协议都是占位入口，点击只弹「功能开发中」；新增 `utils/features.js` 开关，未开通入口直接隐藏（上线改 `true`） |
| 2026-10-28 | 本地 `scp/ssh ecommerce` 报 Could not resolve hostname | `~/.ssh/config` 没有别名；加 `Host ecommerce` 或直接用 `root@IP` |
| 2026-10-28 | 服务器 `docker pull` 401 Unauthorized | 服务器未 `docker login`；在服务器执行 `docker login ccr.ccs.tencentyun.com -u 账号` |
| 2026-10-28 | root 密码 SSH 被拒 | 改用密钥；宝塔终端把公钥写入 `/root/.ssh/authorized_keys`（目录 700、文件 600） |
| 2026-10-28 | compose diff 整文件变化 | Windows CRLF vs 服务器 LF；用 `diff --strip-trailing-cr` 或 `sed -i 's/\r$//'` |
| 2026-10-26 | 物流轨迹出现未来时间（今天发货显示明天轨迹） | 假轨迹按发货时间 + 2/6/18/22/24 小时生成，未过滤未来项；新增 `addTraceIfPast`，只返回当前时间之前已发生的轨迹 |
| 2026-10-26 | 上传分类 webp 后容器内仍只有旧 png，接口 404 | 整个 `upload` 目录被替换，bind mount 钉在旧 inode；`docker compose up -d --force-recreate product-service user-service` 重新挂载 + 清 CF 缓存 |
| 2026-10-26 | 小程序发行提示 `utils/subscribe.js` 主包未使用 | 文件只被订单分包使用；移到 `pages/order/subscribe.js` 并改引用 |
| 2026-10-25 | 小程序审核白屏、商品全空 | 提审传了 `dist/dev`（BASE_URL=127.0.0.1）；改传 `dist/build/mp-weixin` |
| 2026-10-25 | `/api/categories` 500，耗时约 10s | 生产未装 Redis；Docker Redis + 密码 + `REDIS_HOST=redis`，代码加降级和 2s 超时 |
| 2026-10-25 | 用户头像真机不显示 | 后端返回 `http://` 头像 URL；SCG 5 移除 X-Forwarded-*，`UrlUtil` 对非本机强制 https |
| 2026-10-25 | 机器人头像 iOS 不显示 | 包内本地 webp 解码不稳定；转成 `static/zongjie.jpg` |
| 2026-10-23 | 营业额折线每天只剩一笔 | 按天 SQL 误用 `GROUP BY pay_at`（按秒分组）；改 `GROUP BY DATE_FORMAT(pay_at,'%Y-%m-%d')`，补 `shop_id`/`is_deleted` 过滤 |
| 2026-10-23 | `GET /api/admin/after-sales/{id}` 500 | `AfterSaleExchangeReturnMapper.xml` namespace 中间断行；写回完整一行 |
| 2026-10-23 | 售后时间带 `T` | `spring.jackson.date-format` 管不到 `LocalDateTime`；common 注册 `JsonMapperBuilderCustomizer` + `LocalDateTimeSerializer` |
| 2026-10-23 | 换货详情退货方式显示「—」 | 创建换货售后未写 `return_way`/`return_time_slot`；Service + SQL 补字段 |

---

*每个阶段完成后或遇到错误时更新此文件*
