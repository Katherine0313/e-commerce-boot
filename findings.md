# 发现与决策

> 本文件只存事实与研究结论。外部网页/搜索内容只写这里，不写进 `task_plan.md`。

## 本地启动流程（开发环境）

> 2026-10-03 补记。起因是一次排查：`java.exe` 只有 IDEA、8080/8848 全无监听，结论是**后端不是启动失败，是根本没起**；而且即使去起，Docker 没开的话也起不来。

### 核心机制：必须先 Nacos，再服务

所有服务在启动阶段都要向 Nacos 注册（各 `application.yml` 里的 `spring.cloud.nacos.discovery.server-addr`，默认 `${NACOS_ADDR:127.0.0.1:8848}`）。
Nacos 只有一份，是 Docker 容器（根目录 `docker-compose.yml`，**该文件只起 Nacos 一件事**）。

**Docker 没起 → Nacos 没起 → 8848 无监听 → 服务注册失败、启动不成功。**

容易踩的误解：「只把 application 跑起来就行」——不行，注册中心不在，服务起不来。

对照：**MySQL 不走 Docker**（本机原生 MySQL80，3306），它不是卡点，Docker 停掉不影响它。

### 启动顺序（缺一不可）

**第 1 步：启动 Docker Desktop，等引擎 Running**

- 判据：`docker info` 能正常返回；WSL 发行版 `docker-desktop` 为 Running
- 注意：Docker Desktop 主程序没起时，`tasklist` 里连 docker 进程都没有

**第 2 步：起 Nacos，并等它就绪**

```powershell
docker compose up -d          # 在项目根目录执行
docker compose ps             # 期望 nacos 状态为 running
docker compose logs -f nacos  # 看启动过程，出现 "Nacos started successfully" 即可
```

Nacos 冷启动约 20~40 秒，**别起完容器立刻起服务**，等 8848 通了再走下一步。
验证：`Test-NetConnection 127.0.0.1 -Port 8848` 返回 True。

**起 Redis（product-service 分类树缓存需要，不测分类接口可跳过）**

本地 Redis 是 Windows 原生版，不占 Docker；路径 `D:\Software\tools\redis\Redis-x64-5.0.14.1`。

```powershell
D:\Software\tools\redis\Redis-x64-5.0.14.1\redis-server.exe D:\Software\tools\redis\Redis-x64-5.0.14.1\redis.windows.conf
```

验证：`Test-NetConnection 127.0.0.1 -Port 6379` 返回 True。

> ⚠️ 不起 Redis 的话，`GET /api/categories`（分类树）会直接 500——`listCategories()` 第一步就查 Redis，连不上就抛异常；商品列表/详情接口不依赖 Redis，不受影响。

**第 3 步：起后端服务**

顺序不强制，先起 gateway 也可以（它靠 `lb://` 懒解析，不要求下游先就绪）。

- 最小可用集（跑通 C 端主链路）：`gateway` + `auth-service` + `user-service`
- 要测商品再加：`product-service`

两种起法，二选一：

1. **IDE 里跑启动类**（日常推荐，改完即时生效）
2. **命令行跑 jar**

```powershell
java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar
java -jar auth-service/target/auth-service-0.0.1-SNAPSHOT.jar
java -jar user-service/target/user-service-0.0.1-SNAPSHOT.jar
```

**第 4 步：验证端到端**

```powershell
curl 127.0.0.1:8080/api/products/1     # 经 gateway 转发，返回商品 JSON 即通
```

### 端口对照表

| 服务 | 端口 | 启动类 | 需要 DB 凭据（application-dev.yml） |
|---|---|---|---|
| gateway | 8080 | `GatewayApplication` | 否 |
| auth-service | 9001 | `AuthServiceApplication` | **是** |
| shop-service | 9002 | `ShopServiceApplication` | 否 |
| product-service | 9003 | `ProductServiceApplication` | **是** |
| order-service | 9004 | `OrderServiceApplication` | 否 |
| aftersale-service | 9005 | `AftersaleServiceApplication` | 否 |
| user-service | 9006 | `UserServiceApplication` | **是** |
| file-service | 9007 | `FileServiceApplication` | 否 |
| Nacos | 8848 / 9848 / 9849 | — | — |
| Nacos 控制台 | 18080（容器内 8080） | — | — |
| MySQL | 3306（本机原生） | — | — |
| Redis | 6379（本机原生，非 Docker） | — | — |

### 已知坑：jar 可能落后于源码

`target/*.jar` 是 2026-09-30 13:48 打的，而 `product-service` 在 10-01 接库时改了 18 个源文件**没有重新打包**，
它的 jar 里**不含 `application-dev.yml`**（源码里有）。直接 `java -jar` 跑它，会用旧代码且拿不到 DB 凭据而启动失败。

所以用 jar 方式起之前，先全量重打一次：

```powershell
mvn -q install -DskipTests
```

（父 pom 未装本地仓库时会报 `Could not find artifact ecommerce:e-commerce-boot:pom`，同样是这条命令解决。）

### 关停

```powershell
docker compose down    # 停 Nacos（数据在容器内嵌 derby，开发期无所谓）
# 后端服务：IDE 里点停止，或命令行 Ctrl+C / taskkill
```

### 排查速查

| 现象 | 大概率原因 |
|---|---|
| `curl 127.0.0.1:8080/...` 连不上（exit 7） | gateway 没起，或 Docker/Nacos 没起导致它起不来 |
| 服务日志停在注册阶段、报 Nacos 连接失败 | Docker Desktop 没起 / Nacos 容器没起 |
| `tasklist` 里 Java 进程只有 IDEA | 后端一个都没起（正常现象，它不会自启） |
| 启动报 `DB_USERNAME` 占位符解析失败 | 该服务的 `application-dev.yml` 缺失（或被 gitignore 后没本地补上） |
| `GET /api/categories` 返回 500 | Redis 没启动（分类树缓存依赖 Redis，连不上就 500）；起 Redis 即可 |

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
| 顾客端 C（小程序） | 用户 user | `user` | `/api/**` | 免登录：启动静默 wx.login 换 openid |
| 顾客端 C（PC 端商城） | 用户 user | `user` | `/api/**` | 账号密码 / 验证码登录 |
| 后台 B（PC 端管理系统） | 管理员 admin | `admin` | `/api/admin/**` | 账号密码登录（必须登录） |

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

## 头像静态资源路由 + 绝对 URL 拼接（2026-10-10）

### 背景：加购/看资料时头像显示不出来

小程序点「加入购物车」未登录会先弹 `LoginPopup`，其 `startLogin` 走 `ensureLogin` + `getProfile`；头像 URL 由 user-service 的 `UserController.fullUrl` 拼成**绝对地址**后直接交给 `<image :src>`（前端不再补域名）。前端 `findings.md` 早已把该静态资源记为 `http://127.0.0.1:8080/upload/avatar/...`，但网关**没有 `/upload/**` 路由**（只有 `/image/**` → product-service）→ 经网关取头像必然 404。

同时确认加购/头像这条链路所需模块（回答「要开哪些服务」）：MySQL（Docker 3307）+ Nacos 8848 + gateway 8080 + user-service 9006（`/api/wxlogin`、`/api/user/profile`、`/api/user/avatar`）+ order-service 9004（`POST /api/cart`）；product-service 9003 只在浏览商品时需要（加购请求本身是 order-service 同库查 `products`），**Redis 不用**（只有 product-service 商品缓存用它）。

### 改动 1：网关补 `/upload/**` → `lb://user-service`

与 `/image/**` 对称。静态资源不在 `/api/**` 内，`JwtInterceptor` 不拦，天然免登录。

### 改动 2：`fullUrl` 读到的 Host 经网关是下游实例地址

- 网关转发时 Host 被重写成目标实例地址（实测经网关是 `192.168.92.1:9006`，即 Nacos 注册的那个实例；直连 9006 时才是 `127.0.0.1:9006`），不是客户端访问的 8080。
- 后果：原实现拼出 `http://192.168.92.1:9006/upload/avatar/xxx.jpg`。开发者工具在本机能加载（本机恰好可达该地址，所以看着「好了」），**真机必坏**。
- **抽到 common**：新增 `common/utils/UrlUtil.java`（`fullUrl` / `forwardHeader`），`UserController` 与 `WxLoginController` 共用，不再各自拼域名。
- 顺带拦外链：已经以 `http(s)://` 开头的 avatar 原样返回，否则会拼成 `http://hosthttps://thirdwx.qlogo.cn/...`（与 `UserServiceImpl.deleteOldAvatarFile` 只认本地 `/upload/avatar/` 前缀的处理一致）。
- ⚠️ 期间一度改成「优先读 `X-Forwarded-Host`」，**实测走不通**，见改动 4。

### 改动 3：`/api/wxlogin` 返回相对路径（渲染层报错的直接根因）

小程序报 `Failed to load local image resource /upload/avatar/9_1791625414298.jpg`（附 500）：

- `/api/user/profile` 的 avatar 走 `fullUrl` 拼域名，但 `/api/wxlogin` 直接把库里原始值返回（`UserServiceImpl.login` 里 `.avatar(user.getAvatar())`）→ 前端 `ensureLogin` 把 `res.avatar`（相对路径）写进 Pinia store → `<image :src="/upload/avatar/...">` 被小程序**当成包内路径**，本地找不到就报这个错。
- 那个 500 **不是后端返回的**：实测同一文件 `curl http://127.0.0.1:8080/upload/avatar/9_1791625414298.jpg` = **200**（直连 9006 也 200），500 出自开发者工具自己的本地静态服务。
- 前端 `findings.md:47` 早已约定「两个 avatar 字段都是完整 URL，前端不要再拼域名」——是实现漏了 wxlogin，故修在 `WxLoginController`：`vo.setAvatar(UrlUtil.fullUrl(request, vo.getAvatar()))`。

### 改动 4：`X-Forwarded-*` 不可信 → 网关保留客户端 Host

用自签 HS512 令牌实测 `GET /api/user/profile`（`JWT_SECRET` 与运行时同值，指纹比对一致；最初 401 是因为 id=9 那个用户已不存在，换库里 10 号用户即 200）：

| 请求 | 返回的 avatar |
|---|---|
| 直连 9006 | `http://127.0.0.1:9006/upload/avatar/10_...jpg`（Host 即客户端值，回退逻辑本身没错） |
| 经网关 8080（不加任何头） | `http://192.168.92.1:9006/upload/avatar/10_...jpg` |
| 经网关 8080 + 客户端自塞 `X-Forwarded-Host: probe1.example.com` | **同上，客户端值被覆盖** |

- 根因（读 `spring-cloud-gateway-server-webflux:5.0.3` 源码）：`XForwardedHeadersFilter` 取的是 `request.getURI().getHost()`——`lb://` 此时已解析成实例地址，所以它产出的 `X-Forwarded-Host` 恒等于下游实例地址，**客户端自带的值也会被覆盖**；`X-Forwarded-Proto` 同理（恒为「网关→下游」的协议）。
- 正解：**网关侧保留客户端 Host**——`gateway/application.yml` 给 `/api/**`（user-service）路由加 `filters: - PreserveHostHeader`（该类在 5.0.3 里存在，配置名有效）；`UrlUtil` 取值顺序改回 **Host 优先、`X-Forwarded-Host` 仅兜底**。
- 协议仍是遗留项：网关对外是 https 时 `X-Forwarded-Proto` 会给出 http，需要在网关/Nginx 层显式透传客户端协议（已写进 `UrlUtil` 注释）。

### 验证

- **静态资源**：经网关 8080 取 `upload/avatar/9_...jpg` = 200，直连 9006 也 200 → `/upload/**` 路由与 `WebMvcConfig` 静态映射都通。
- **接口返回**：见改动 4 的表（自签令牌实测），据此才发现 `X-Forwarded-*` 这条路不可用。
- **待验证**：`PreserveHostHeader` + Host 优先改完后需重启 gateway 与 user-service 再测一遍，期望经网关返回 `http://127.0.0.1:8080/upload/avatar/...`。
- 教训：`X-Forwarded-*` 由网关自己生成，别想当然当作「客户端原始信息」——本项目里它等于下游实例地址。

## SQL 书写规范：关键字统一大写（2026-10-11）

> 起因：杰米指出 `AddressMapper.xml` 里 `values` 小写「不规范」。核实后确认这**原本是全项目统一风格**（11 个 mapper 全是小写），讨论后决定全量改为大写，并固化为后续 mapper 的书写规范。

### 规范

| 类别 | 规范 | 示例 |
|---|---|---|
| 关键字 | **全大写** | `SELECT` `FROM` `WHERE` `AND` `OR` `ORDER BY` `ASC`/`DESC` `LIMIT` `AS` `IN` `ON` `INSERT INTO` `VALUES` `UPDATE` `SET` `DELETE FROM` `LEFT JOIN` `JOIN` |
| 内置函数 | **全大写** | `NOW()` `COUNT(*)` |
| 表名 / 列名 | 保持小写 | `address`、`shop_id`、`created_at` |
| 别名 | 保持小写 | `c`、`p`、`t`、`item`、`pid` |
| 反引号包裹的表名 | 原样不动 | `` `user` ``、`` `admin` ``（MySQL 关键字，必须反引号） |
| `#{...}` 占位符、`<if>`/`<foreach>` 标签及 OGNL 表达式 | 不动 | `#{userId}`、`test="payType != null"` |

### 注意事项

- **功能零影响**：MySQL 的 SQL 关键字大小写不敏感，`values` / `VALUES` / `Values` 在服务端完全等价。本次改的全是关键字，表名/列名/别名一个字符未变。
- **真正区分大小写的是表名/库名**，且仅在 Linux + `lower_case_table_names=0` 时生效；本项目表名全小写，不受影响。
- **新增 mapper 照此写**，别再写小写关键字。
- **`target/classes/` 下的同名 mapper 是编译产物**，不要手改；重新 build（或 IDEA Run）会自动覆盖。
- 本次涉及 11 个文件：`auth-service`（AdminMapper）、`order-service`（Address/Cart/OrderItem/Order/Product Mapper）、`product-service`（Category/Product/Tag Mapper）、`user-service`（Cart/User Mapper）。
- **复核方法**：grep 时先排除以 `<` 开头的 XML 标签行（`<select>`/`<insert>` 等标签名本身就是小写，是 MyBatis 语法，不是 SQL）与 `namespace`/`resultType`/`id=` 这类 Java 类名行，剩下的实际 SQL 行应无小写关键字。

## 下单幂等（createOrder 幂等键，2026-10-14）

> 小程序要上线，下单接口（`POST /api/orders`）此前无任何防重，弱网重试 / 双击会重复下单、扣两次库存。给 `createOrder` 加幂等键。

### 语义

`idempotencyKey` = 一次「下单意图」的标识，不是一次请求的标识。用户发起一次下单时前端生成 UUID，这次下单的所有重试复用同一个 key；下单成功返回后前端立即丢弃，下次再下单重新生成。

### Redis key 设计

| 项 | 值 |
|---|---|
| key | `order:idempotency:{userId}:{idempotencyKey}` |
| value | `PENDING`（处理中）或 `orderId`（已成功） |
| TTL | 24 小时（覆盖 30 分钟待付款超时 + 用户中途退出重进） |

key 带 userId，天然隔离不同用户（越权拿不到别人的 key）。

### 后端流程

1. 校验基础字段 + `idempotencyKey` 非空（空则 400「缺少幂等键」）。
2. 读 redisKey：命中 `PENDING` → 409「订单处理中」；命中 `orderId` → `getOrder` 返回原订单（内部校验归属）；未命中 → 继续。
3. `SETNX` 占位 `PENDING`：失败 → 409「订单处理中」（并发竞争，毫秒级，几乎不触发）。
4. 走原下单逻辑（`doCreateOrder`：查商品 → 校验库存 → 乐观锁扣库存 → 写订单+明细）。
5. 成功：redisKey 改写成 `orderId`；失败：删除占位，重新抛出（允许重试）。

### 降级（Redis 挂了不阻断下单）

4 个 `safe*` 方法包住 Redis 调用，异常记日志后降级：

- `safeGet` → 返回 null（视为无缓存，走正常下单）
- `safeSetIfAbsent` → 返回 true（视为占位成功，继续下单）
- `safeSet` / `safeDelete` → 忽略

Redis 宕机 = 幂等自动失效、退化为普通下单。

### 前端约定（必同步，2026-10-14 已实现）

1. 用户发起下单时生成 `idempotencyKey = UUID`（小程序 `crypto.randomUUID()`）。
2. 下单成功 → 立即丢弃 key。
3. 下单失败/超时 → 重试复用同一个 key。
4. 用户取消、改地址/商品重新提交 → 重新生成 key。

> 前端已落地：`api/order.js` 的 `genIdempotencyKey()` 生成 key；下单确认页 `pages/order/confirm.vue`
> 在 `onLoad` 生成一次、本页内失败重试复用同一个 key。下单/支付封装在 `api/order.js`（`createOrder` /
> `payOrder`），「我已支付」= 下单 + 立即支付一步完成（暂未接微信支付，`payType` 传占位 `wechat`）。
> 三条入口共用确认页：首页/分类页「立即购买」、购物车「去结算」、购物车「猜你喜欢」点商品。

### 改动文件

- `order-service/.../dto/CreateOrderDTO.java`：加 `idempotencyKey`（@Schema）。
- `order-service/.../service/impl/OrderServiceImpl.java`：注入 StringRedisTemplate；createOrder 加幂等逻辑；抽 `doCreateOrder`；4 个 `safe*` 方法。
- `order-service/.../resources/application.yml`：补 `spring.data.redis`（`REDIS_HOST`/`REDIS_PORT` 环境变量，本地默认 127.0.0.1:6379）。

### 两个已知边界

- **Redis 与 DB 时序**：Redis 写 `orderId` 在 DB 事务提交前（Spring 事务只管 DB）。极端情况 DB 提交失败、Redis 却写了 orderId，下次命中后 `getOrder` 查不到订单抛「订单不存在」。概率极低且不超卖（库存乐观锁兜底），暂不加自愈。
- **并发窗口**：两个相同 key 同时到达，第二个请求读到 `PENDING` 返回 409「处理中」。毫秒级，几乎不触发，前端提示「稍后查看订单列表」即可。

## 订单软删 + 订单时间时区（2026-10-16）

### 1. 订单软删（deleteOrder）
- 语义：用户删除订单只置 `is_deleted=1`，不物理删；`listByUser` 查询带 `WHERE is_deleted=0`，软删后列表自动不返回
- 接口：`DELETE /api/orders/{id}`；只校验归属（`getOwnedOrder`），不限状态（任何状态都能删，已取消/已完成订单也要能清理）
- 前端：订单列表「删除订单」按钮直接删（不弹确认），成功后刷新列表

### 2. 订单时间时区坑（待付款倒计时差 8 小时）
- 现象：待付款订单倒计时一直显示「超时关闭」，实际刚下单
- 根因：MySQL 容器时区是 UTC，`NOW()` 生成的 `created_at` 比 JVM（UTC+8）少 8 小时；而订单号 `orderNo` 用 Java `LocalDateTime.now()` 是北京时间，两者差了 8 小时
- 排查线索：同一条订单 `orderNo` 时间戳是 `21:11:11`，`createdAt` 却是 `13:11:11`，差值正好 8 小时
- 修复：`OrderServiceImpl.doCreateOrder` 的 `createdAt` 改用 `LocalDateTime.now()`（JVM 北京时间），`OrderMapper.xml` insert 的 `NOW()` 改 `#{createdAt}`，与 `orderNo` 同源
- 遗留：`updateStatus` 里 `pay_at`/`ship_at`/`confirm_at`/`finish_at` 仍是 `NOW()`（UTC），后续展示这些时间字段时需一并统一成 Java 时间

