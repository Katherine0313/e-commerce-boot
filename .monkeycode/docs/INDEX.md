# e-commerce-boot 文档

面向后端贡献者与对接前端的 Wiki：架构、网关路由、模块职责与领域概念。接口细节以 springdoc 为准。

**快速链接**: [架构](./ARCHITECTURE.md) | [接口](./INTERFACES.md) | [开发者指南](./DEVELOPER_GUIDE.md)

---

## 核心文档

### [架构](./ARCHITECTURE.md)

微服务切分、技术栈、网关与数据流。

### [接口](./INTERFACES.md)

按服务列出 Controller 路径、认证与 Swagger JSON。

### [开发者指南](./DEVELOPER_GUIDE.md)

本地 Nacos/MySQL/Redis、环境变量占位、加接口步骤。

---

## 模块

| 模块 | 描述 | README |
|------|------|--------|
| gateway | 8080 路由与文档聚合 | [gateway](./模块/gateway.md) |
| common | JWT、Result、促销、日志 | [common](./模块/common.md) |
| product-service | 商品库存内容 | [product-service](./模块/product-service.md) |
| order-service | 交易与报表 | [order-service](./模块/order-service.md) |
| aftersale-service | 售后 | [aftersale-service](./模块/aftersale-service.md) |
| auth-service | 管理员登录 | [auth-service](./模块/auth-service.md) |
| user-service | C 端用户与兜底 /api | [user-service](./模块/user-service.md) |
| system-service | RBAC 与监控 | [system-service](./模块/system-service.md) |

专题运维仍见仓库 `docs/` 与 `deploy/README.md`。

---

## 核心概念

| 概念 | 描述 |
|------|------|
| [订单](./专有概念/Order.md) | 下单支付发货完成 |
| [售后](./专有概念/AfterSale.md) | 退款退货换货 |
| [商品](./专有概念/Product.md) | SPU/SKU/库存/促销 |
| [RBAC](./专有概念/RBAC.md) | 后台登录与权限 |

---

## 入门指南

1. [架构](./ARCHITECTURE.md)
2. [核心概念](#核心概念)
3. [开发者指南](./DEVELOPER_GUIDE.md)
4. [接口](./INTERFACES.md) + `http://localhost:8080/swagger-ui/index.html`

### 需要集成？

前端只打网关 8080；路由以 `gateway/.../application.yml` 为准。

---

## 快速参考

```bash
docker compose up -d nacos
./mvnw clean package -DskipTests
```

| 文件 | 目的 |
|------|------|
| `pom.xml` | 8 服务 + common |
| `gateway/.../application.yml` | 路由 |
| `docs/swagger-addresses.md` | Swagger 速查 |
| `deploy/README.md` | 生产部署 |
