# 接口文档

对外统一走网关 `http://localhost:8080`。认证：C 端/管理端多数接口在请求头携带 `Authorization`（JWT）。成功响应 HTTP 200 + `Result`：`{ success, data, message, code }`。

权威字段与试调以 springdoc 为准：`http://localhost:8080/swagger-ui/index.html`。下列为代码中的路径清单（非完整 schema）。

## 认证方式

| 场景 | 机制 |
|------|------|
| 管理员 | `POST /api/login` 发 JWT；`JwtInterceptor` 校验；`@RequirePerm` 按钮权限 |
| C 端微信 | `POST /api/wxlogin` |
| C 端 PC | `/api/user/login`、`/register`、`/send-code`、`/login-code` |
| 内部 AI | Header `X-Internal-Token`，路径 `/internal/ai/**` |
| 静态资源 | `/image/**`、`/upload/**` 免登录 |

## auth-service（`/api`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/login` | 管理员登录 |
| GET | `/api/auth/captcha` | 图形验证码 |
| GET | `/api/admin/info` | 当前管理员 |
| POST | `/api/logout` | 登出（记日志） |

## user-service

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/wxlogin` | 微信登录 |
| GET/POST | `/api/user/profile` 等 | 资料、头像、改名、改手机、注销 |
| POST | `/api/user/login` `/register` `/send-code` `/login-code` | PC 登录注册 |
| GET | `/api/member/profile` `/levels` | 会员 |
| POST | `/api/feedback` | 反馈 |
| GET | `/api/admin/members` `/{id}` | 会员管理 |
| POST | `/api/admin/members/{id}/refresh` | 刷新会员 |
| GET/PUT | `/api/admin/member-levels` | 等级 |
| GET | `/api/admin/users/statistics` | 用户统计 |

网关其余 `/api/**` 兜底到 user-service。

## product-service

C 端：`GET /api/home`，`/api/products/**`，`/api/categories/**`，`/api/tags/**`，`/api/favorites/**`，`/api/banners/**`，`/api/articles/**`，上传 `/api/upload/**`。

管理端：`/api/admin/products|categories|tags|specs|stock|promotions|upload|banners|articles|article-categories`。

内部：`GET/PUT /internal/ai/product/{id}`，`GET /internal/ai/products`，`GET /internal/ai/stock/{skuId}`。

## order-service

C 端：`/api/cart/**`，`/api/orders/**`（含 quote/pay/ship/finish/cancel/review），`/api/address/**`，`/api/coupons/**`，`/api/reviews/product/{productId}`。

管理端：`/api/admin/orders|coupons|report|reviews|deliveries|expresses|finance`。

内部：`GET /internal/ai/orders`。

WebSocket：`/ws/**`（来单提醒，握手鉴权在 order-service）。

## aftersale-service

C 端 `/api/after-sales`：创建、列表、分页、counts、状态常量、按订单查、详情、提交退货、取件完成、日志。

管理端 `/api/admin/after-sales`：列表、详情、日志、更新、删除。

内部：`GET /internal/ai/aftersale/{id}`。

## system-service

前缀 `/api/admin/system/**` 与 `/api/admin/monitor/**`：菜单、角色、用户、操作/登录日志、缓存/在线用户/服务器/任务监控、API 文档同步、数据库表与备份。

common 还注册 `GET/POST /api/admin/tasks/**`（各服务可监控任务），是否被网关打到 system 取决于路径：`/api/admin/tasks` 不在 system 谓词内，可能落到 user-service 兜底。**以网关 Path 实际匹配为准。**

## file-service

模块存在且网关聚合了 `/file-service/v3/api-docs`，源码侧当前仅有 `FileServiceApplication`，无 Controller。无法确定对外业务接口。

## AI（Python，非本仓库）

网关：`/api/agent/**`，`/api/mall/ai/**`，`/api/admin/ai/**` → `AI_SERVICE_URL`。

## Apifox 导入 JSON

| 服务 | 地址 |
|------|------|
| auth | `http://localhost:8080/auth-service/v3/api-docs` |
| user | `http://localhost:8080/user-service/v3/api-docs` |
| product | `http://localhost:8080/product-service/v3/api-docs` |
| order | `http://localhost:8080/order-service/v3/api-docs` |
| aftersale | `http://localhost:8080/aftersale-service/v3/api-docs` |
| file | `http://localhost:8080/file-service/v3/api-docs` |
| system | `http://localhost:8080/system-service/v3/api-docs` |
