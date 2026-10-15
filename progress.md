# 进度日志

> 历史会话流水账已精简，只留「当前进度」快照。详细踩坑 / 决策见 `findings.md` 与 `task_plan.md`。

## 当前进度（五问重启检查）

| 问题 | 答案 |
|------|------|
| 我在哪里？ | 阶段 4 order-service 剩余缺口（购物车 3 缺口）+ 阶段 5 aftersale 未开工；阶段 11 后台实时提醒已立项待触发 |
| 我要去哪里？ | ①补购物车 3 缺口；②阶段 5 aftersale-service；③取消订单关联售后单；④阶段 11 等「后台嫌手动刷新」时启动 |
| 目标是什么？ | 拆成真·微服务部署，跑通 Nacos 注册 + Gateway 路由，再逐个补业务服务 |
| 我学到了什么？ | 见 findings.md（头像静态资源、购物车、订单域、WebSocket 预研、订单软删+时区）；静态资源地址统一拼、别各拼各的；`lb://` 长连接与 ThreadLocal 在 WS 下失效、跨实例推送走 Redis pub/sub；订单时间用 Java `LocalDateTime.now()` 别用 MySQL `NOW()`（时区差 8h） |
| 我做了什么？ | 阶段 0–9：8 服务骨架、auth 登录、商品域迁移、AI Agent、推荐服务、Redis 分类缓存、订单域迁移+购物车、订单功能、用户软删+注销、地址模块、下单幂等、小程序下单链路；阶段 AC：订单软删接口 + 订单时间时区修复 |

## 错误日志（最近）

| 时间 | 错误 | 解决 |
|------|------|------|
| 2026-10-15 | order-service 启动报 `UndeclaredThrowableException`（Nacos 注册失败） | Docker Desktop 没开 → 起 Docker + `docker compose up -d`；纯练 WebSocket 可加 `--spring.cloud.nacos.discovery.enabled=false` 跳过注册 |

---

*每个阶段完成后或遇到错误时更新此文件*
