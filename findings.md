# 发现与决策

> 本文件只存事实与研究结论。外部网页/搜索内容只写这里，不写进 `task_plan.md`。

## 需求

- 用户要求：探索本项目，生成 `task_plan.md`、`findings.md`、`progress.md` 三个持久化规划文件。
- 未指定业务改动范围，因此本次**只做只读探索 + 建文档**，不改任何 Java / 配置代码。

## 项目架构约定（原 `project_spec.md`）

> 2026-09-28 由根目录 `project_spec.md` **全文并入**本节，原文件已删除（见 `progress.md` 同日会话）。
> 微信小程序、PC 端商城（网页版）、PC 端后台管理系统（Vue）三个前端共用一个后端（本项目）。本文记录模块划分与命名规则，后续开发照此执行。

### 核心原则

按 **业务模块** 建包，不按「端」建包。每个业务模块内部「数据共用、接口分层」。

- 不建 `wx`（小程序端）大包 + `pc`（PC 端商城）大包 + `admin`（后台）大包，而是 `goods` / `order` / `aftersale` 这种业务包。
- 同一份业务数据多端共用，只是接口、权限、可见字段不同。

### 三端划分

| 端 | 身份 | JWT type | 路径前缀 | 说明 |
|---|---|---|---|---|
| 顾客端 C（小程序） | 用户 user | `user` | `/api/**` | wx.login 换 openid 登录 |
| 顾客端 C（PC 端商城） | 用户 user | `user` | `/api/**` | 账号密码 / 验证码登录 |
| 后台 B（PC 端管理系统） | 管理员 admin | `admin` | `/api/admin/**` | 账号密码登录 |

小程序与 PC 端商城 **共用同一个用户账号体系**（同一张 `user` 表、同一套 C 端业务接口），只是登录方式不同。真正区分端的地方只有两处：**登录入口** 和 **前端 UI**，后端业务逻辑是同一套。

### 目录结构

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

### 命名与路径规则

- **C 端（顾客）= `User` 前缀**：`UserGoodsController`、`UserOrderController`。小程序与 PC 端商城共用这套接口。
- **登录入口 = 按渠道前缀**：`WxLoginController`（小程序）、`PcLoginController`（PC 端商城，预留），因为它们本身就是「端」的差异所在。
- **后台 = `Admin` 前缀**：`AdminGoodsController`、`AdminOrderController`
- **路径**：C 端 `/api/**`，后台 `/api/admin/**`
- **身份类型**：JWT 里 `type` 常量 `user` / `admin`，由 `JwtInterceptor` 按路径分流校验（`/api/admin/**` → admin，其余 → user）

### 各层拆分规则

| 层 | 是否按端拆 | 说明 |
| --- | --- | --- |
| entity | 共用 | 多端操作同一张表，只写一份 |
| mapper | 共用 | 同上 |
| service | 按「C 端 / 后台」拆 | C 端（小程序 + PC 端商城）共用一份 User*Service，后台 Admin*Service 流程不同 |
| controller | 按「C 端 / 后台」拆 | `User*` / `Admin*` 前缀区分；登录入口再按渠道 `Wx` / `Pc` 分 |
| dto / vo | 按「C 端 / 后台」拆 | 可见 / 可写字段不同（后台看成本价、库存，顾客端不该看到） |

> 关键点：C 端不按「小程序 / PC 端商城」拆，因为两者的业务逻辑完全一样，拆了就是复制粘贴。若某天 PC 端商城确有差异字段，属于 vo 层裁剪，不拆 service。

### 身份模块（例外）

`user`（用户/顾客）与 `admin`（管理员）是两个不同实体、不同表、不同登录方式，各自独立建模块，不共用。

- 用户：`user` 表。小程序 `wx.login()` 换 openid 登录；PC 端商城账号密码 / 验证码登录。同一账号可同时绑定 openid + 手机号。
- 后台管理员：`admin` 表 + RBAC（角色 / 权限单独建表），账号 + 密码登录。

### 迁移落地顺序

贴合分阶段迁移策略：小程序先跑通（`user` + 各业务模块的 `User*` 部分），PC 端商城接入时补登录入口（`PcLoginController`），后台功能之后逐个模块补 `Admin*` 部分。结构先留好位置，往里填即可。

### 接口文档同步约定

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
- **免登录清单只有一份**：`WebMvcConfig.EXCLUDE_AUTH_PATHS`。拦截器豁免和文档补全都读它（同一套 Ant 匹配语义，支持 `/api/products/**` 前缀豁免），新增免登录接口（登录入口、C 端商品浏览等）只改这一处。
- **运行时文档**：springdoc 自动生成的 `/swagger-ui.html`、`/v3/api-docs` 无需手动维护；`docs/openapi.yaml` 是另一份手工契约，两者都要保持一致。

### 头像上传遗留待办

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

## 研究发现

### 1. 技术栈（`pom.xml`）

| 项 | 值 |
|---|---|
| Spring Boot | 4.1.0（parent） |
| Java | 17 |
| Web | `spring-boot-starter-webmvc`（Boot 4 的新名字）+ `starter-json` + `starter-thymeleaf` |
| ORM | `mybatis-spring-boot-starter` 4.0.1（XML mapper） |
| 数据库 | `mysql-connector-j`（runtime） |
| JWT | jjwt 0.13.0（api / impl / jackson 三件套） |
| 文档 | `springdoc-openapi-starter-webmvc-ui` 3.1.1 |
| HTTP 客户端 | `spring-boot-restclient`（提供 `RestClient.Builder` + `spring.http.client.*` 超时） |
| 其它 | Lombok |
| 构建 | `spring-boot-maven-plugin`，带 `-Xshare:off` |

- 注意：代码里 `ObjectMapper` 的包是 `tools.jackson.databind`（Jackson 3.x），不是老的 `com.fasterxml.jackson`，与 Boot 4 配套。读代码时别按老包名找。

### 2. 目录结构与模块划分

```
ecommerce
├── common/ Result.java                    // 统一响应体
├── configuration/ WebMvcConfig / CorsConfig / OpenApiConfig
├── exception/ BusinessException / GlobalExceptionHandler
├── interceptor/ JwtInterceptor
├── properties/ JwtProperties / WeChatProperties
├── utils/ HttpClientUtil / JwtUtil / UserContext
├── user/                                  // 目前唯一成型的业务模块
│   ├── controller/ WxLoginController、UserController
│   ├── service/ UserService + impl/UserServiceImpl
│   ├── mapper/ UserMapper（+ resources/mappers/user/UserMapper.xml）
│   ├── entity/ User
│   ├── dto/ WxLoginDTO
│   └── vo/ WxLoginVO、UserProfileVO
└── Application.java
```

- `user` 模块内部**已按 controller/service/mapper/entity/dto/vo 分层**，符合既定规范。
- 其他业务模块（`admin` / `goods` / `order` / `aftersale`）**尚未建包**，属预留。
- `@MapperScan("ecommerce.**.mapper")` 是刻意的窄扫描：`Application` 类注释写明，裸 `@MapperScan` 会把 `UserService` 这类接口也注册成 Mapper 代理，导致 Controller 注入到代理而非实现类。

### 3. 现有接口（3 个）

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/api/wxlogin` | 免登录 | `WxLoginDTO{code}` → `WxLoginVO{token,id,name,avatar}`；首次登录自动注册 |
| GET | `/api/user/profile` | 需登录 | → `UserProfileVO`，`avatar` 已拼为完整 URL |
| POST | `/api/user/avatar` | 需登录 | `multipart/form-data`，字段 `file` → 完整 URL 字符串 |

### 4. 四条核心链路

**登录**：`WxLoginController.wxLogin` → `UserServiceImpl.login(code)`
1. code 空 → 400；2. 带 appid/secret/js_code 请求 `https://api.weixin.qq.com/sns/jscode2session`；3. 响应无 `openid` → 400；4. `findByOpenid` 查不到则 `insertUser`（`useGeneratedKeys` 回填 id）；5. `jwtUtil.generateUserToken(id)` 返回。

**鉴权**（`JwtInterceptor`，只拦 `/api/**`，豁免 `EXCLUDE_AUTH_PATHS`：登录入口 `/api/wxlogin`、`/api/login`、`/api/logout`，以及 C 端商品浏览 `/api/products/**`、`/api/categories/**`、`/api/tags/**`）
1. 非 `HandlerMethod`（静态资源、预检）直接放行；2. 取 `Authorization`（带不带 `Bearer ` 都认）；3. 解析失败 → 401；4. 按路径定 `requiredType`（`/api/admin/**` → admin，其余 → user），不匹配 → 403；5. `subject` 写入 `UserContext`（ThreadLocal）。

**资料**：`UserContext.getUserId()` → `findById` → 查不到抛 401 → 组装 `UserProfileVO` → Controller 用 `scheme://Host + 相对路径` 拼完整 URL。

**头像上传**：`normalizeContentType`（小写、去 `;` 后参数）→ 查 `ALLOWED_IMAGE_TYPES`（png/jpeg/gif/webp）→ 落盘 `upload/avatar/{userId}_{时间戳}.{后缀}` → 写库相对路径 `/upload/avatar/xxx` → 删旧文件（只删本地 `/upload/avatar/` 前缀的）。

### 5. 约定（必须遵守）

- **响应结构**：`Result<T>{success, data, message, code}`；成功 HTTP 200，失败返回**真实**状态码。
- **异常出口**：业务异常抛 `BusinessException(status, msg)`，由 `GlobalExceptionHandler` 统一转 `Result`；兜底分支对 `ErrorResponse` 保留原始状态码（404/405 不会被误报成 500）；异常消息不回传前端。
- **静态映射**：`WebMvcConfig` 把 `/upload/**` 映射到 `file:upload/`（相对运行目录）。
- **文档同步**：接口有改动，`docs/openapi.yaml` 必须手工同步（它不会自动更新）；公共码引用 `components/responses`，失败响应体统一 `ResultVoid`。
- **上传接口必须声明 `consumes = MULTIPART_FORM_DATA_VALUE`**：否则 runtime 文档变成 JSON 请求体，且 413 注入规则失效。
- **跨域**：`CorsConfig` 目前 `addAllowedOriginPattern("*")` + `allowCredentials(true)`，注释标明是开发期放开，生产要收紧。

### 6. 配置与安全姿态

- `application.yml` 里 `DB_USERNAME` / `DB_PASSWORD` / `JWT_SECRET` / `WECHAT_APPID` / `WECHAT_SECRET` 都**故意不给默认值**，缺失即启动失败；真实值放 `application-dev.yml`（已 gitignore）或用环境变量。
- `spring.profiles.active: dev` → 本地必须有 `application-dev.yml` 才能起。
- 上传限制 `max-file-size: 2MB` / `max-request-size: 3MB`，超限由 `GlobalExceptionHandler` 转 413 + 可读提示。
- springdoc 默认**关闭**（`springdoc.api-docs.enabled: false`，`swagger-ui.enabled: false`），只在 dev profile 开——生产即使配错 profile 也不暴露 `/swagger-ui.html`。
- `JwtProperties` / `WeChatProperties` 都在 `@PostConstruct` 里做启动校验。
- JWT：`expire: 604800`（7 天），密钥长度不足 32 字节 jjwt 直接抛 `WeakKeyException`（不允许带病启动）。

### 7. 遗留与不一致清单

| # | 项 | 现状 | 备注 |
|---|---|---|---|
| 1 | 上传目录硬编码 | `UserServiceImpl.UPLOAD_DIR = "upload/avatar/"` 与 `WebMvcConfig` 的 `file:upload/` 靠人工对齐 | 「项目架构约定」已列为待办：抽成配置项 |
| 2 | PC 端商城登录入口 | 规范里预留 `PcLoginController` / `/api/login`，`EXCLUDE_AUTH_PATHS` 已把它算进来，但**类还没写** | 预留位已就绪 |
| 3 | 后台 admin 模块 | 登录接口已就位（`auth-service`：`POST /api/login`、`GET /api/user/info`、`POST /api/logout` + `docs/sql/admin.sql` 建表脚本）；**admin 表待执行建表脚本，RBAC 权限表未做** | 登录接口已就位（2026-09-30） |
| 4 | 4xx 文档缺口 | 运行时文档不给上传接口注入 415（`consumes` 不匹配时实际会返回 415），`docs/openapi.yaml` 也没有 415 | 代码注释里承认 415 是预期行为，但没进文档 |
| 5 | `WxLoginVO.avatar` 语义 | 登录返回的是**相对路径**，`UserProfileVO.avatar` 返回**完整 URL** | `openapi.yaml` 已分别写清，但 Java 侧 `@Schema` 没提这层差异 |
| 6 | 上传校验边界 | Content-Type 由客户端声明、可伪造，白名单只挡「误传 svg / 可执行文件」 | 「项目架构约定」明确：要挡伪装文件得读文件头；上线前需真机验证一次 |
| 7 | 临时测试类 | `src/test/java/ecommerce/TempTimeoutVerify.java`，类注释写着「临时校验类…跑完即删」，仍在仓库里 | 验证 RestClient 读超时用，非本次范围 |


### 8. 未提交改动（重要）

`git status`：`M WebMvcConfig.java`、`MM UserController.java`。用 `git diff HEAD` 看真实差异：

- **`UserController.java` 相对 HEAD 是质量回退**：HEAD 版本有类级 Javadoc、显式 import、`private String fullUrl(...)`、以及「只写 `@Operation`，200 由 springdoc 自动生成」「必须声明 `consumes`」两段关键说明注释；工作区换成了 `import ...annotation.*` 通配符、`public String fullUrl`、`//接口前缀`，还多了一段「这是java的多行注释 / 可以注释多行文本」的占位注释。
- **`WebMvcConfig.java` 丢了注释**：`addResourceHandlers` 上「头像等上传文件的静态访问：/upload/** 映射到本地 upload/ 目录」被删掉，缩进风格也改了。
- **暂存区与工作区不一致**：`git diff --cached` 显示暂存区的 `UserController` 比 HEAD **少 58 行（类体基本为空）**。此时提交会先落一个丢接口的版本。→ 已列入 `task_plan.md` 关键问题 1、2，**需用户确认是刻意练习还是误操作**，未擅自改动。

## 技术决策

| 决策 | 理由 |
|------|------|
| 按业务模块建包（`user`/`goods`/`order`/`aftersale`），模块内分 controller/service/mapper/entity/dto/vo | 数据多端共用，按端建包会复制粘贴；见本文档「项目架构约定」 |
| C 端 `User*` 前缀（小程序 + PC 商城共用），后台 `Admin*` 前缀，路径 `/api/**` 与 `/api/admin/**` | 三端共用后端，只按「端」区分登录入口与 VO 字段裁剪 |
| 失败返回真实 HTTP 状态码，不恒 200 | 前端可按 HTTP 语义判断；`Result` 只作结构补充 |
| 公共失败响应在 `OpenApiConfig` 全局注入（`GlobalOpenApiCustomizer` 而非 `OpenApiCustomizer`） | 避免每接口重复注解；后者按分组各跑一次会重复注入 |
| 免登录清单唯一来源 `EXCLUDE_AUTH_PATHS` | 拦截器豁免与文档补全读同一份，防漂移 |
| C 端商品浏览免登录（列表/详情/分类/标签），加购、下单需登录 | 小程序进首页看商品不带 token；写操作走购物车/订单接口需 user 令牌；后台商品管理走 `/api/admin/**` 需 admin 令牌 |
| 头像存相对路径、返回时拼完整 URL | 换域名不失效 |

## 遇到的问题

| 问题 | 解决方案 |
|------|---------|
| `git diff` 与 `git status` 的 `MM` 对不上，看不出真实改动 | 分别用 `git diff --cached`（HEAD→暂存区）和 `git diff HEAD`（HEAD→工作区）比对，确认存在两份不同改动 |
| 记忆中的 `ARCHITECTURE.md` 在根目录不存在 | 实际文件是 `project_spec.md`；按用户指示已把它全文并入本文档「项目架构约定」一节并删除原文件，记忆指针同步改到本节 |

## 遗留风险（已决策暂不修，勿反复提起）

| 风险 | 影响 | 当前处置 |
|---|---|---|
| `JwtInterceptor.afterCompletion` 在鉴权失败（preHandle 抛异常）时不执行，`UserContext` 的 ThreadLocal 未清除 | Tomcat 线程复用，理论上可能读到上一个请求的 userId | 用户已决定暂不修 |
| `wx_user` / `user` 表 `openid` 排序规则大小写不敏感 | 理论上有登录串号风险 | 用户已决定暂不修 |
| 数据库凭据曾进入 git 历史，MySQL 侧明文密码未处理 | 凭据泄露面 | 配置已移出版本库，历史与库侧未清理 |
| `CorsConfig` 放开所有来源 + `allowCredentials(true)` | 仅开发期可接受 | 注释已标注生产需收紧 |

## 资源

### 三个相关项目地址

| 项目 | 本地路径 | 说明 |
|---|---|---|
| 电商小程序（C 端 uniapp） | `D:\study\my-workspace\wechat-workspace\e-commerce-uniapp` | 顾客端小程序，对应 `user` 模块 / `/api/**` |
| 电商后台管理系统前端 | `D:\study\my-workspace\wechat-workspace\e-commerce` | Vue3 + ant-design-vue，对应 `admin` 模块 |
| 电商后台管理系统后端（待重构） | `D:\study\my-workspace\wechat-workspace\e-commerce\server` | Node.js（Express + SQLite），准备整合进本项目做微服务拆分 |

- 架构与约定入口：**本文档「项目架构约定」一节**（2026-09-28 由原根目录 `project_spec.md` 全文并入，含三端划分、命名规则、OpenAPI 同步约定、头像上传待办表）
- 静态接口契约：`docs/openapi.yaml`（263 行）
- 运行时文档：`/swagger-ui.html`、`/v3/api-docs`（仅 dev profile 开）
- 配置：`src/main/resources/application.yml`；本地真实值在 `application-dev.yml`（gitignore）

## Node 后端重构探索（2026-09-30）

> 目标：放弃 `e-commerce/server`（Node.js），整合到本 Spring 项目，让前端 `e-commerce` 对接，做微服务拆分。已确认两项决策：**保留多租户沙箱** + **真·多服务部署**。

### 被替代的 Node 后端

| 项 | 值 |
|---|---|
| 框架 | Express 4 + jsonwebtoken + multer + sanitize-html |
| 数据库 | SQLite（`sql.js` 整库内存，每次写全量导出覆盖落盘） |
| 端口 | 3000，只监听 `127.0.0.1`，走 nginx 反代 |
| 单实例 | `util/singleInstance` 闸门（sql.js 特性，防双进程写盘冲突） |

**核心架构 = 多租户「店铺沙箱」**：
- `sqlite/shopTables.js` 是唯一事实来源：`SHOP_TABLES` 登记 17 张业务表（categories / tags / products / stock / business_onsale / users / consignee_address / orders / order_items / after_sales / after_sale_items / after_sale_returns / after_sale_exchange_returns / after_sale_only_refunds / cart / uploads），全部按 `shopId` 隔离。
- 模板店 `TEMPLATE_SHOP_ID=1`（杰米小店）：所有沙箱的数据源，永不过期；`resolveShop` 中间件从 token 取 shopId 注入 `req.shopId`，DB 层据此过滤；`templateGuard` 禁写模板店。
- `ID_BLOCK=1000000`：id 号段偏移隔离（模板店 0 号段、沙箱 N 号段），保证多店 id 不冲突——这是 sql.js 单库下的 hack。
- 沙箱生命周期：开沙箱克隆模板店数据；`ShopDB.sweepExpired()` 每小时回收超过 24h 的沙箱；限额每 IP 200/小时、全站 2000 存活（单沙箱约 56KB）。

### 12 个业务域

auth 登录 / shops 沙箱 / products+categories+tags+parameters 商品 / upload 上传 / address 地址 / orders 订单 / cart 购物车 / after-sales 售后 / stock 库存预警 / business-onsale 促销。数据库按 `ProductDB/TagDB/CategoryDB/ConsigneeAddressDB/OrderDB/UserDB/CartDB/AfterSalesDB/StockDB/BusinessOnsaleDB/UploadDB` 分文件。

### 前端（要对接）

- Vue3 + ant-design-vue + axios + pinia + vue-router；`axios.defaults.baseURL = VITE_API_BASE_URL`。
- 接口全部 `/api/**`（无版本前缀）；登录 `POST /api/login`（admin/admin123）+ `GET /api/user/info`，登出 `POST /api/logout`。
- **这是后台管理系统，对应 Spring 的 `admin` 模块**；响应结构 `{success,data,message}` 与 Spring `Result{success,data,message,code}` 基本兼容；401 跳登录页、403 弹 message。
- 前端接口清单（按 api 文件）：products 7、categories 6、tags 9、orders 14、cart 5、after-sales 7、address 4、stock 3、shop 2、auth 2。

### 微服务版本兼容（2026 基线，需阶段 0 实测）

| 组件 | 版本 |
|---|---|
| Spring Boot | 4.1.0（现状） |
| Spring Cloud | 2025.1.x |
| Spring Cloud Alibaba | 2025.1.0.0（官方已适配 Boot 4） |
| Nacos | server 3.1.1（稳定）/ 3.3.0-BETA（支持 Boot4 + Jackson3） |
| JDK | 17（现状满足） |

**关键坑**（落地阶段 0 必须逐个验证/处理）：
1. Jackson 2→3：Boot 4 用 `tools.jackson`，旧的 jackson2 依赖会 ClassCastException——本项目的 `ObjectMapper` 已是 `tools.jackson.databind`，天然对齐。
2. SCA 2025.1.x 废弃 `bootstrap.yml`，配置中心改用 `spring.config.import: optional:nacos:...`。
3. Nacos 3.x console 端口分离（8080），客户端通信仍 8848；3.0+ 默认开启鉴权，需配 username/password。
4. Nacos 3.x 不自动建表，需手动初始化 DB。
5. 升级顺序：JDK → Boot → Cloud → SCA，跳版本易踩坑。

### 阶段 0 实测结果（2026-09-30，Nacos 部署踩坑）

Nacos + 服务注册 + 网关路由已跑通（task_plan 阶段 0 completed）。实测发现的坑：

1. **镜像 tag 带 v 前缀**：`nacos/nacos-server:v3.1.1`（不是 `3.1.1`），后者 Docker Hub 上不存在。
2. **启动脚本强制校验三个变量非空**：`NACOS_AUTH_TOKEN` / `NACOS_AUTH_IDENTITY_KEY` / `NACOS_AUTH_IDENTITY_VALUE`，即使 `NACOS_AUTH_ENABLE=false`（关鉴权）也必须设，否则容器逐个报 `env ... must be set` 并 `exit 255`。
3. **3.x 开启鉴权后 `nacos/nacos` 登录报 `User not found`**：默认账号机制与 2.x 不同（2.x 内置 nacos/nacos，3.x 需另行初始化）。开发期决定**关闭鉴权**（`NACOS_AUTH_ENABLE=false`），生产开启前先解决默认账号初始化问题。
4. **console 端口确认在 8080**：容器内日志 `Nacos Console 3.1.1 ... Port: 8080`；compose 映射 `18080:8080` 避开 gateway 的 8080。客户端通信仍 8848（`nacos registry ... register finished` 确认）。
5. **Docker Desktop 用户级安装**：装在 `%LOCALAPPDATA%\Programs\DockerDesktop`（非默认 `C:\Program Files\Docker`），其 `resources\bin` 需新开终端才会进 PATH。
6. **MySQL 复用本机**：本机 MySQL80 服务已在跑（3306 占用、库 `e_commerce_db` 已建好），Docker 不再起 MySQL，避免端口冲突。

实测注册日志（后续服务接入的对照模板）：
- auth-service：`nacos registry, DEFAULT_GROUP auth-service 192.168.1.2:9001 register finished`
- user-service：`nacos registry, DEFAULT_GROUP user-service 192.168.1.2:9006 register finished`
- gateway：`nacos registry, DEFAULT_GROUP gateway 192.168.1.2:8080 register finished`
- 网关转发验证：`curl localhost:8080/api/user/profile` 与直连 `localhost:9006/api/user/profile` 返回一致（HTTP 401 + Result 结构），证明 `lb://user-service` 路由打通。

### 与现状的差异 / 要重构的点

1. SQLite→MySQL：表结构重设计，去掉 `ID_BLOCK` 号段 hack（MySQL 全局自增 + 查询带 shopId 过滤即可）。
2. 单实例锁→去掉（MySQL 多实例可并发写，反而是微服务多实例的前提）。
3. 模板店克隆 = shop-service 里「按 shopId=1 查出数据 → 批量 insert 成新 shopId」的数据复制操作。
4. ~~JWT claims 扩展：需加 `shopId`（多租户隔离键）+ `role`~~ → **已实现**：`JwtUtil.generateAdminToken` 现在把 `id` / `username` / `role` / `shopId` 都签进 payload。
5. ~~现有 `Result` / `GlobalExceptionHandler` / `JwtInterceptor` / `UserContext` / `BusinessException` 要下沉为各服务共用的 common 模块~~ → **已完成**（阶段 D，common 模块 + 8 服务骨架）。
6. 现有 `user` 模块（小程序）与后台 `admin` 模块是两个账号体系：admin 走账号密码（本次前端对接的主体，登录接口已就位），user 走微信 openid（已有）。
7. 路径约定冲突（**待决策，用户说「到时候再说」**）：本文档「项目架构约定」写后台接口走 `/api/admin/**`，但实际前端（Node 版后台管理系统）全部走 `/api/**`（无 admin 前缀）。`JwtInterceptor.isAdminPath` 按路径分流会把 admin 令牌在 `/api/user/info` 等非 `/api/admin` 接口判成「需 user 类型」→ 403。做 gateway 鉴权过滤器时再定改法。

## 商品域数据库迁移踩坑点（2026-10-01）

> 商品首页数据源已从 Node 的 SQLite 迁到 MySQL（product-service 接库，见 `docs/sql/product.sql`）。只迁商品域 4 张表（shops / categories / tags / products），32 个商品（含下架）全迁，图片转 webp 迁入。**以下踩坑点后续迁移后台模块（用户/订单/售后）同样适用，务必先看。**

### 1. MySQL 客户端 GBK 乱码（最坑）

- **现象**：`mysql < xxx.sql` 导入 UTF-8 的 SQL，中文全乱码（商品名/分类名/表列 COMMENT 变成「鍟嗗搧」这类字）。
- **根因**：Windows 下 mysql 客户端默认 `character_set_client/connection/results = gbk`，而库/表是 utf8mb4，UTF-8 文件被按 GBK 误解码（双重编码）。
- **迷惑点**：用 GBK 连接查询，乱码会被「假还原」成正常中文（双重编码抵消）；**必须用 `--default-character-set=utf8mb4` 查询才能看到真实乱码**。
- **解决**：导入和查询一律加 `--default-character-set=utf8mb4`。
- **根治**：`C:\ProgramData\MySQL\MySQL Server 8.0\my.ini` 的 `[client]` 段加 `default-character-set=utf8mb4`，重启 MySQL。

### 2. 幂等脚本（IF NOT EXISTS + INSERT IGNORE）的局限

- `CREATE TABLE IF NOT EXISTS`：表已存在就**跳过建表**，改表结构（加/删索引、改 COMMENT）**不生效** → 必须 DROP 重建。
- `INSERT IGNORE`：主键冲突就**跳过插入**，改数据**不覆盖** → 必须 TRUNCATE 后重导。
- 结论：改完 `product.sql` 想让它生效 = **DROP 4 张表 + 重新导入**，两步缺一不可。

### 3. 图片迁移的路径/格式错位

- Node 端 `public/image/` 目录**根本不存在**，真实图片在 `e-commerce/server/uploads/`，命名为数字 `1.png~32.png + 13.jpeg`（对应商品 id）。
- 数据库 `image` 存 `/image/upload/N.webp`，磁盘却是 `.png/.jpeg`（**扩展名不匹配**）；`body` 字段的 `/image/body/UUID.webp` 磁盘**全部不存在**。
- id=9 的 `image` 是 UUID（`530c8603-...` 脏数据），真实图是 `9.png`。
- **处理**：Pillow 把 png/jpeg 转 webp → `upload/image/upload/N.webp`；`WebMvcConfig` 加 `/image/**` → `file:upload/image/`；gateway 加 `/image/**` → product-service；id=9 改回 `/image/upload/9.webp`。
- body 图磁盘全丢，用户确认「不用管，没影响」（首页列表只用主图）。

### 4. 数据库操作边界

- 我只做**查询**；建表 / TRUNCATE / DROP / UPDATE 都生成脚本由用户自己执行（会话里用 `!` 前缀跑命令）。

### 5. 建表设计决策与注意事项（商品域，后续模块照此执行）

- 只迁商品域 4 表，不迁全部 17 表。
- 去掉 SQLite `ID_BLOCK=1000000` 号段 hack → `BIGINT AUTO_INCREMENT`（查询带 shop_id 过滤）。
- Unix 秒 → `DATETIME`（`FROM_UNIXTIME`）；REAL → `DECIMAL`。
- `categories.product_count` 冗余列去掉（读走子查询实时算）；`tags.product_count` 保留（物化计数）。

**外键（最终决策：加物理外键，级联由 DB 兜底，不在应用层手写）**

| 外键 | 关系 | 删除规则 |
|------|------|---------|
| `fk_products_tag` | products.tag_id → tags.id | SET NULL |
| `fk_products_category` | products.category_id → categories.id | SET NULL |
| `fk_categories_parent` | categories.parent_id → categories.id | CASCADE |

- SET NULL 对应原 SQLite 的 `ON DELETE SET NULL`（删标签/分类 → 商品自动解绑）；CASCADE 是用户选的（删父分类 → 级联删全部子孙分类）。
- 为什么加：用户实际只用模板店（shop_id=1）、不克隆沙箱、商品域同库，物理外键可行且更可靠（级联不会漏）。早前「沙箱克隆要关外键 / 外键不感知 shop_id」两条顾虑在单模板店场景下不成立。
- `categories.parent_id` 自引用 CASCADE 会递归级联（删父→删子→删孙）；与 products 的 SET NULL 协同：删一个分类，其下所有子孙分类被级联删、指向它们的商品只解绑不删。
- **迁移时清理了一处原系统脏数据**：孤儿分类 id=801「女士连衣裙」`parent_id=8` 指向已删除的父分类（正是原系统删父分类没同步子分类的实证）。不加外键时它是悬空引用；加了外键则导入违反约束，故删除该行。

**索引（最终决策：只留主键 + 外键自动索引）**

- 手动加的普通 `KEY` 全部去掉（数据量小加不加无差别；以后需要时 `ALTER TABLE ... ADD INDEX` 再补，多租户下 `shop_id` 是未来最该补的列）。
- 外键列（tag_id / category_id / parent_id）上的索引是 InnoDB 建外键时**自动创建**的，删不掉也不该删（删了外键失效）；对 JOIN 查询反而是优化。
- 别混淆：早前「去索引」针对的是手动普通索引，与外键自动索引不冲突。

## 视觉/浏览器发现

- 本次探索读了 Node 后端（`e-commerce/server`）与前端（`e-commerce`）的本地代码与配置，并用 WebSearch 确认了 Spring Cloud Alibaba / Nacos 对 Spring Boot 4 的版本兼容（见本节「微服务版本兼容」）。

---
*每执行2次查看/浏览器/搜索操作后更新此文件*
*防止视觉信息丢失*
