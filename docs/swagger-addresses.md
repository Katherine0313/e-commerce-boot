# 各服务接口文档（Swagger）地址速查

接口文档由 springdoc 根据代码里的 `@Schema` / `@Operation` / `@Tag` 注解**自动生成**，随代码实时更新，以这里为准（已废弃手写的 `docs/openapi.yaml`）。

## 统一入口（推荐）

网关已做聚合，**一个地址看全部服务**，页面顶部下拉切换：

**http://localhost:8080/swagger-ui.html**

> 该地址会 302 跳转到 `/swagger-ui/index.html`（两者都已实测可用）；若浏览器没自动跟随跳转，直接用 http://localhost:8080/swagger-ui/index.html

## 访问前提

1. **dev 环境**：springdoc 在 `application-dev.yml` 里已开启（`enabled: true`），本地默认 `spring.profiles.active: dev`。
2. **服务要启动**：需连上 Nacos（`127.0.0.1:8848`）和 MySQL。聚合入口还需启动 gateway（8080）和各下游服务，下拉里对应服务的文档才会加载出来。

> `swagger-ui.html` 是图形界面（能点开字段、能直接试调）；`v3/api-docs` 是纯 JSON 规范。

## 前端对接提醒

前端请求统一走网关 `http://localhost:8080`（不是各服务的 900x 端口），路由规则见 `gateway/src/main/resources/application.yml`。
