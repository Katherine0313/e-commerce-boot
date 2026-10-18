# 各服务接口文档（Swagger）地址速查

接口文档由 springdoc 根据代码里的 `@Schema` / `@Operation` / `@Tag` 注解**自动生成**，随代码实时更新，以这里为准（已废弃手写的 `docs/openapi.yaml`）。

## 统一入口（推荐）

网关已做聚合，**一个地址看全部服务**，页面顶部下拉切换：

**http://localhost:8080/swagger-ui.html**

> 该地址会 302 跳转到 `/swagger-ui/index.html`（两者都已实测可用）；若浏览器没自动跟随跳转，直接用 http://localhost:8080/swagger-ui/index.html

## 访问前提

1. **开启 springdoc**：各下游服务的接口文档由环境变量 `SPRINGDOC_ENABLED` 控制，默认关闭（`${SPRINGDOC_ENABLED:false}`，安全兜底、避免生产暴露）。启动下游服务时需加环境变量 `SPRINGDOC_ENABLED=true`（IDEA 的 Run Configuration → Environment variables，或命令行 `SPRINGDOC_ENABLED=true java -jar ...`），否则聚合页里该服务的下拉项会 404 加载不出。gateway 本身是 `enabled: true`，聚合页始终能打开。
2. **服务要启动**：需连上 Nacos（`127.0.0.1:8848`）和 MySQL。聚合入口还需启动 gateway（8080）和各下游服务，下拉里对应服务的文档才会加载出来。

> `swagger-ui.html` 是图形界面（能点开字段、能直接试调）；`v3/api-docs` 是纯 JSON 规范。

## 前端对接提醒

前端请求统一走网关 `http://localhost:8080`（不是各服务的 900x 端口），路由规则见 `gateway/src/main/resources/application.yml`。

## 导入 Apifox

网关只在 UI 层聚合下拉，**没有「合并全部服务」的单一 JSON 地址**，导入 Apifox 需**逐个服务**导入，导入的是各服务的 OpenAPI 规范 JSON（不是 `.html`）：

| 服务 | JSON 地址 |
|------|-----------|
| 登录 auth-service | `http://localhost:8080/auth-service/v3/api-docs` |
| 用户/购物车 user-service | `http://localhost:8080/user-service/v3/api-docs` |
| 商品 product-service | `http://localhost:8080/product-service/v3/api-docs` |
| 订单/地址 order-service | `http://localhost:8080/order-service/v3/api-docs` |
| 售后 aftersale-service | `http://localhost:8080/aftersale-service/v3/api-docs` |
| 店铺 shop-service | `http://localhost:8080/shop-service/v3/api-docs` |
| 文件 file-service | `http://localhost:8080/file-service/v3/api-docs` |
| AI 助手 ai-agent-service | `http://localhost:8080/ai-agent-service/v3/api-docs` |

**导入方式**

- Apifox 桌面版：导入 → OpenAPI/Swagger → URL 导入，粘贴上表 JSON 地址。
- Apifox 网页版 / SaaS 版（云端访问不到本机 localhost）：浏览器打开上表 JSON 地址 → `Ctrl+S` 另存为 `.json` → 导入 → 上传该文件。

前提同「访问前提」：下游服务需 `SPRINGDOC_ENABLED=true` 启动，浏览器能打开该 JSON 地址看到内容才算就绪。
