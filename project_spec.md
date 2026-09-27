# 后端架构约定

> 微信小程序、PC 端商城（网页版）、PC 端后台管理系统（Vue）三个前端共用一个后端（本项目）。本文记录模块划分与命名规则，后续开发照此执行。

## 核心原则

按 **业务模块** 建包，不按「端」建包。每个业务模块内部「数据共用、接口分层」。

- 不建 `wx`（小程序端）大包 + `pc`（PC 端商城）大包 + `admin`（后台）大包，而是 `goods` / `order` / `aftersale` 这种业务包。
- 同一份业务数据多端共用，只是接口、权限、可见字段不同。

## 三端划分

| 端 | 身份 | JWT type | 路径前缀 | 说明 |
|---|---|---|---|---|
| 顾客端 C（小程序） | 用户 user | `user` | `/api/**` | wx.login 换 openid 登录 |
| 顾客端 C（PC 端商城） | 用户 user | `user` | `/api/**` | 账号密码 / 验证码登录 |
| 后台 B（PC 端管理系统） | 管理员 admin | `admin` | `/api/admin/**` | 账号密码登录 |

小程序与 PC 端商城 **共用同一个用户账号体系**（同一张 `user` 表、同一套 C 端业务接口），只是登录方式不同。真正区分端的地方只有两处：**登录入口** 和 **前端 UI**，后端业务逻辑是同一套。

## 目录结构

```
ecommerce
├── common/  configuration/  exception/  interceptor/  properties/  utils/
│   （框架层，多端共用）
│
├── user         # 用户/顾客（小程序 + PC 端商城共用）
│   ├── controller/  WxLoginController  /api/wxlogin   （小程序登录）
│   │                PcLoginController  /api/login     （PC 端商城登录，预留）
│   ├── service/     UserService（含各登录方式 + 用户数据操作）
│   ├── mapper/      UserMapper
│   ├── entity/      User
│   └── dto/  vo/    WxLoginDTO / WxLoginVO 等
│
├── admin         # 管理员 / 角色 / 权限（只有后台，RBAC 单独建表，与 user 分开）
│
├── goods         # 商品 —— 两端都有（C 端 + 后台）
│   ├── controller/  UserGoodsController   /api/goods/**
│   │                AdminGoodsController  /api/admin/goods/**
│   ├── service/     UserGoodsService、AdminGoodsService
│   ├── mapper/      GoodsMapper             ← 共用
│   ├── entity/      Goods                   ← 共用
│   └── dto/  vo/    按端拆（User... / Admin...）
│
├── order         # 订单 —— 两端都有，同上
│
└── aftersale     # 售后 —— 两端都有，同上
```

## 命名与路径规则

- **C 端（顾客）= `User` 前缀**：`UserGoodsController`、`UserOrderController`。小程序与 PC 端商城共用这套接口。
- **登录入口 = 按渠道前缀**：`WxLoginController`（小程序）、`PcLoginController`（PC 端商城，预留），因为它们本身就是「端」的差异所在。
- **后台 = `Admin` 前缀**：`AdminGoodsController`、`AdminOrderController`
- **路径**：C 端 `/api/**`，后台 `/api/admin/**`
- **身份类型**：JWT 里 `type` 常量 `user` / `admin`，由 `JwtInterceptor` 按路径分流校验（`/api/admin/**` → admin，其余 → user）

## 各层拆分规则

| 层 | 是否按端拆 | 说明 |
| --- | --- | --- |
| entity | 共用 | 多端操作同一张表，只写一份 |
| mapper | 共用 | 同上 |
| service | 按「C 端 / 后台」拆 | C 端（小程序 + PC 端商城）共用一份 User*Service，后台 Admin*Service 流程不同 |
| controller | 按「C 端 / 后台」拆 | `User*` / `Admin*` 前缀区分；登录入口再按渠道 `Wx` / `Pc` 分 |
| dto / vo | 按「C 端 / 后台」拆 | 可见 / 可写字段不同（后台看成本价、库存，顾客端不该看到） |

> 关键点：C 端不按「小程序 / PC 端商城」拆，因为两者的业务逻辑完全一样，拆了就是复制粘贴。若某天 PC 端商城确有差异字段，属于 vo 层裁剪，不拆 service。

## 身份模块（例外）

`user`（用户/顾客）与 `admin`（管理员）是两个不同实体、不同表、不同登录方式，各自独立建模块，不共用。

- 用户：`user` 表。小程序 `wx.login()` 换 openid 登录；PC 端商城账号密码 / 验证码登录。同一账号可同时绑定 openid + 手机号。
- 后台管理员：`admin` 表 + RBAC（角色 / 权限单独建表），账号 + 密码登录。

## 迁移落地顺序

贴合分阶段迁移策略：小程序先跑通（`user` + 各业务模块的 `User*` 部分），PC 端商城接入时补登录入口（`PcLoginController`），后台功能之后逐个模块补 `Admin*` 部分。结构先留好位置，往里填即可。

## 接口文档同步约定

> 后端接口有改动，`docs/openapi.yaml` 必须同步，不能只改代码。

- **同步时机**：新增 / 修改 / 删除 Controller 接口，或 DTO / VO 字段变化时，同步更新 `docs/openapi.yaml`。
- **为什么**：`docs/openapi.yaml` 是给前端看的静态契约文档，不会随代码自动更新，必须手动同步。
- **响应结构**：统一按 `Result<T>` 的实际字段写 —— `success` / `data` / `message` / `code`；HTTP 状态码真实返回（成功 200，失败 400/401/403/404/500），不要沿用旧的 `code`/`msg`、HTTP 恒 200 的写法。
- **要不要带令牌怎么声明**：根级 `security: - bearerAuth: []` 表示全文档默认要带令牌，登录接口用 `security: []` 单独关掉；`commonFailureResponsesCustomizer` 按 `EXCLUDE_AUTH_PATHS` 自动补这个覆盖，Swagger UI 上登录接口不显示锁图标。令牌方案 `bearerAuth`（`type: http` / `scheme: bearer` / `bearerFormat: JWT`）在 `components.securitySchemes` 里定义一次。
  - **`security` 不产生 401/403 响应**：它只声明「需要哪种鉴权」，这两件事互不相干，别指望写了 `security` 就不用管失败响应。
- **公共失败响应全局注入，Controller 上不写任何 `@ApiResponse`**：`OpenApiConfig#commonFailureResponsesCustomizer` 按下面的规则统一注入，并把所有失败响应体统一成 `ResultVoid`。
  | 码 | 注入范围 | 产出方 |
  |---|---|---|
  | `400` | 所有接口 | `GlobalExceptionHandler#handleUnreadable` |
  | `401` / `403` | 需登录接口（`/api/**` 且不在 `EXCLUDE_AUTH_PATHS`） | `JwtInterceptor` |
  | `500` | 所有接口 | `GlobalExceptionHandler#handleOther` 兜底 |
  | `413` | 只有带 `multipart/form-data` 请求体的接口 | `GlobalExceptionHandler#handleMaxUploadSize` |

  - **判据是「这个码由谁产出」，不是「看起来像不像业务码」**。凡是 `GlobalExceptionHandler` 统一产出的就是公共码，一律全局注入；只有该接口**独有语义**的码才值得本地写注解。按此判据，目前**没有任何接口需要本地注解**——`uploadAvatar` 的 413/500 曾被误当作业务码留在本地，是错的：413 出自全局的上传超限处理器，500 出自全局兜底分支，两者每个接口都可能返回。
  - 用的是 `GlobalOpenApiCustomizer` 而不是 `OpenApiCustomizer`：后者按 `GroupedOpenApi` 分组各跑一次，将来分了组会把响应重复注入。
  - 已在本接口显式声明过的码**不会**被覆盖（`containsKey` 守卫），留作将来真有接口独有码时的逃生口。
  - **413 只注入带 `multipart/form-data` 的接口**：不筛的话 `wxlogin` 这种根本不能上传的接口也会挂上 413。所以这条规则依赖下一个 bullet 的 `consumes` 声明，两者是配套的。
- **上传接口必须声明 `consumes`**：`@PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)`。不写的话 springdoc 会套用默认的 `application/json`，运行时文档里这个接口的请求体就变成 JSON —— Swagger UI 给的是 JSON 编辑器而不是文件选择框，插了 `format: binary` 也没用；同时 413 的注入规则也会失效（判不出这是上传接口）。声明后非 multipart 请求会返回 415，这是预期行为。
- **`docs/openapi.yaml` 里的公共码**引用 `components/responses` 下的定义（`BadRequest` / `Unauthorized` / `Forbidden` / `PayloadTooLarge` / `InternalServerError`），失败响应体统一引用 `components/schemas/ResultVoid`。静态契约不会自动更新，新增接口时按上表手工补上对应码。
- **写 `@ApiResponses` 的两个坑**（当前代码里已无任何本地注解，但将来真要写时必须知道）：
  1. 方法上一旦标了 `@ApiResponses`，springdoc 就**不再自动补 200**，成功响应必须显式写 `@ApiResponse(responseCode = "200", useReturnTypeSchema = true)`，否则 Swagger UI 里该接口只剩失败码。零注解的接口才享受自动 200。
  2. 手写的失败码如果不给 `content`，springdoc 会拿方法返回值类型去填响应体（把 400 的 `data` 写成头像 URL）。所以失败响应体一律交给 `commonFailureResponsesCustomizer` 纠正，注解里不要自己写 `content`。
- **免登录清单只有一份**：`WebMvcConfig.EXCLUDE_AUTH_PATHS`。拦截器豁免和文档补全都读它，新增登录入口只改这一处。
- **运行时文档**：springdoc 自动生成的 `/swagger-ui.html`、`/v3/api-docs` 无需手动维护；`docs/openapi.yaml` 是另一份手工契约，两者都要保持一致。

## 头像上传遗留待办

> 头像上传（`POST /api/user/avatar`）核心闭环已完成：**库存相对路径、返回时拼完整 URL**（换域名不失效）。
>
> 2026-09-27 完成前两项：
> - **图片类型白名单**：`UserServiceImpl.ALLOWED_IMAGE_TYPES` 只收 `image/png` / `image/jpeg` / `image/gif` / `image/webp`，挡掉可内嵌脚本的 `image/svg+xml`；白名单同时给出规范后缀，修掉旧写法从 Content-Type 切出 `svg+xml` 这种非法扩展名的问题。比对前先归一化 Content-Type（小写、去掉 `;` 后的参数）。
> - **上传大小限制**：`spring.servlet.multipart.max-file-size: 2MB` / `max-request-size: 3MB`，配在 `application.yml`（非密配置）。超限由 `GlobalExceptionHandler` 转成 413 + 可读提示。
>   - ⚠️ **修正一处旧判断**：Spring Boot 4 / Spring Framework 7 的 `MaxUploadSizeExceededException` **本身实现了 `ErrorResponse`**，不接也会返回 413（只是提示是无信息量的「请求不合法」），并不是旧文档写的「超限返回 500」。单独接它是为了给出可读提示。
>
> 上传校验的两个已知边界：
> - **Content-Type 由客户端声明、可伪造**，白名单只能挡住「误传 svg / 可执行文件」这类情况；要挡「伪装成 png 的恶意文件」得读文件头做 magic number 校验。
> - 若小程序 `wx.uploadFile` 没带上规范的 `image/*`（例如发成 `application/octet-stream`），会被白名单拒掉。**上线前用真机验证一次上传**，必要时按实际 Content-Type 调整白名单。
>
> 以下 1 项是遗留改进点。

| # | 待办 | 现状 | 目标改法 |
|---|---|---|---|
| 1 | 上传目录配置化 | `UserServiceImpl` 硬编码 `upload/avatar/`，与 `WebMvcConfig` 的 `file:upload/` 需手动对齐 | 抽成配置项（如 `app.upload.dir`），两处读同一配置 |
