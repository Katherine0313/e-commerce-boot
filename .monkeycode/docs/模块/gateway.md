# gateway

Spring Cloud Gateway，端口 8080，Nacos 负载均衡转发，并聚合 springdoc。

## 结构

```
gateway/
├── GatewayApplication.java
└── resources/application.yml   # 全部路由
```

## 关键路由优先级（摘录）

1. 各服务 `/<name>/v3/api-docs` RewritePath
2. auth 精确路径
3. system `/api/admin/system`、`/monitor`
4. product（含 `/image/**`）
5. order（含购物车订单财务评价）
6. aftersale
7. AI 静态 URI
8. `/internal/ai/**` 分服务
9. `/ws/**` → order
10. `/upload/**` → user（PreserveHostHeader 在 user `/api/**`）
11. `/api/**` 兜底 user-service

## 依赖

Nacos；下游实例；`AI_SERVICE_URL`。
