# 进度日志

> 历史会话流水账已精简，只留「当前进度」快照。详细踩坑 / 决策见 `findings.md` 与 `task_plan.md`。

## 当前进度（五问重启检查）

- **我在哪里？**
  2026-10-20 后台商品/分类/标签/订单/库存预警/售后 CRUD 已打通（Admin 接口 + e-commerce 前端 goods/category/tag/orders/stock/after-sales 页对接，统一驼峰契约）；售后后台审核/删除已实现（主表状态+商家备注+子表时间戳）；PageVO 分页加 page/pageSize 由后端返回、前端从响应读。剩余：参数/促销、取消订单关联售后单、购物车 2 缺口、file-service、端到端验证。

- **我要去哪里？**
  1. 促销/参数表（business_onsale + parameters）
  2. 后台工作台接口（shops/current、dashboard、/upload）
  3. 取消订单自动生成退款售后单（关联 order-service）
  4. 补购物车 2 缺口
  5. file-service 图片上传
  6. 端到端验证全链路
  7. 阶段 11 等「后台嫌手动刷新」时启动

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
  - 2026-10-19：后台商品/分类/标签 CRUD 打通（Admin*Controller + mapper + e-commerce 前端 goods/category/tag 页对接，统一驼峰契约）；stock 表建 + 30 条数据迁；确立后台 Admin 端对接契约（见 findings「后台 Admin 端对接契约」）
  - 2026-10-19：后台订单管理打通（AdminOrderController 12 接口 + e-commerce 前端 orders 页对接，状态字符串→数字 code、统一驼峰）；OrderStatusEnum 加 color、OrderVO 加 userId、OrderItemVO 加 stock；订单列表后端分页（4 tab 独立请求）+ 发货页全量 + 日期筛选（dateField 白名单）+ LocalDateTime 加 @JsonFormat
  - 2026-10-20：库存预警接口（AdminStockController + StockList 页，路径 /api/admin/stock）；售后后台审核/删除（AdminAfterSaleController，审核=主表状态+商家备注+子表时间戳白名单 STATUS_COLUMN_MAP，删除=主表/明细/子表软删）；订单/售后支付方式 payTypeDesc 中文修复（前端订单 6 表格 payType→payTypeDesc）；售后前端字段蛇形→驼形对齐 + 列表搜索/日期筛选；PageVO 加 page/pageSize 由后端返回、前端从响应读（不写死 pageSize）

## 错误日志（最近）

| 时间 | 错误 | 解决 |
|------|------|------|
| 2026-10-15 | order-service 启动报 `UndeclaredThrowableException`（Nacos 注册失败） | Docker Desktop 没开 → 起 Docker 重启；纯练 WS 可加 `--spring.cloud.nacos.discovery.enabled=false` 跳过注册 |
| 2026-10-18 | 登录报 `admin` 表不存在 | 应用连错库（3306 本机 MySQL80 旧 schema），数据在 3307 Docker MySQL；最终将数据迁回本机 3306，MySQL 统一本地 3306 |
| 2026-10-18 | 前端跨域 CORS 无头 / 头重复 | gateway 是 WebFlux 无 CORS；加 `CorsWebFilter` + 去重 `GlobalFilter` 解决 |

---

*每个阶段完成后或遇到错误时更新此文件*
