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

## AI 导购 Agent 服务（2026-10-03）

> 小程序要加悬浮式 AI 导购助手。定位是**完整 Agent，不是「LLM 聊天壳」**——这是项目亮点。保留文档《智能商品推荐助手项目.docx》四大能力：Graph 工作流（意图路由）/ RAG / MCP / Function Calling。**不用 Spring AI Alibaba，用 Spring AI 官方**。

### 技术选型与兼容性

| 组件 | 选型 | 说明 |
|---|---|---|
| AI 框架 | **Spring AI 2.0.1（官方）** | 正式支持 Boot 4.0/4.1；不用 Spring AI Alibaba（其稳定版 1.1.x 不支持 Boot 4、会 ClassNotFound，对齐 Boot 4 的只有 2.0.0-M1 里程碑） |
| LLM | 硅基流动 OpenAI 兼容接口 | `base-url=https://api.siliconflow.cn/v1`，模型 `deepseek-ai/DeepSeek-V4-Flash`；key 放 application-dev.yml（gitignore） |
| Embedding | 硅基流动 `BAAI/bge-m3` | RAG 向量化 |
| 向量库 | SimpleVectorStore（开发） | 内存起步零部署；生产换 Milvus/PgVector 只改 Bean |
| 服务调用 | OpenFeign | 调 product-service（项目里首个引入 OpenFeign 的服务） |
| MCP | spring-ai-starter-mcp-server-webmvc | `@McpTool` 暴露工具，端点 /mcp/message |

### 四大能力落地

- **Graph 工作流**：`IntentRouter` 意图路由（RECOMMEND/ORDER/QA/CHAT）+ `AiChatServiceImpl` 按意图组合 system prompt + 工具 / RAG。
- **RAG**：`DocumentLoader`（商品→文档）→ `EmbeddingService`（向量化）→ `RagRetriever`（检索 Top-K 拼 Prompt）。
- **MCP**：`@McpTool` 把商品/下单工具暴露为 MCP server，新增数据源不改 Agent 核心。
- **Function Calling**：`@Tool` 定义 `searchProducts`/`getProduct`/`placeOrder`；下单是写操作走 HITL（依赖 order-service 下单接口，已预留）。

### 验证结果（2026-10-03 实测）

- ✅ 启动 + Nacos 注册：`ai-agent-service ...:9008 register finished`
- ✅ 对话链路：ChatClient 调硅基流动 DeepSeek 返回真实回复（key 有效）
- ✅ Tool Calling：问「推荐键盘」触发 searchProducts 工具调用
- ✅ SSE 流式：`data:你好 / data:呀 ...` 打字机输出正常（Flux<String> 在 Spring MVC 下 SSE 可用）
- ✅ MCP：`Registered tools: 2`，端点 /mcp/message
- ⚠️ 推荐返回 DeepSeek 编的键盘（非真实商品）：product-service 未启动、Feign 调不到真实数据 → 幻觉，印证「接真实数据降幻觉」

### Spring AI 2.0 关键 API（与 1.x 不同，写代码先看）

- `@Tool` / `@ToolParam`：`org.springframework.ai.tool.annotation.*`
- `@McpTool` / `@McpToolParam`：`org.springframework.ai.mcp.annotation.*`
- 配置扁平化：`spring.ai.openai.chat.model`（不再嵌套 `.options`）
- `SimpleVectorStore.builder(EmbeddingModel).build()`
- 工具调用只在 ChatClient 走（`ToolCallingAdvisor`），直接调 ChatModel 不触发

## 推荐服务（Python）方案（2026-10-04）

> 小程序首页要加「猜你喜欢」模块，依据**订单表下单记录**（不是浏览/收藏）推荐商品。推荐算法用 Python 写，独立成一个服务；Java 只做桥接；前端走网关。

### 决策定案

| 项 | 决策 |
|---|---|
| 数据源 | 订单表 `orders` + `order_items`（核心是 `user_id → product_id` 关系，通过 join orders 拿 user_id）。先读原 Node 的 SQLite，order-service 落地后迁 MySQL；Python 侧抽 `datasource` 抽象层，迁库只改配置开关 |
| 服务形态 | Python FastAPI 独立服务，端口 9009；只被 Java 内部调用，不暴露给小程序（统一走网关鉴权，无跨域） |
| 算法（第一版） | RFM 分群 + KMeans 聚类 + 群体热销推荐（同簇热销过滤已购）+ 销量热门兜底 |
| 冷启动兜底 | 未登录 / 未下单 / 新用户 → 按销量降序推荐全量在售商品（销量 = `sum(quantity)`，无销量商品按 id 排后补位） |
| Java 桥接 | 放 product-service，`GET /api/products/recommend`，复用网关已有 `/api/products/**` 路由（**网关零改动**）；RestClient 调 Python，超时/异常走热门兜底，首页不白屏 |
| 前端 | 复用 `ProductVO` 商品卡片，数据结构零改动 |

### 现状事实（数据已核实）

- 当前 MySQL `e_commerce_db` 只有 5 张表（categories/products/shops/tags/user），**orders/order_items 尚未迁入**（order-service 在 task_plan 阶段 4，pending）。
- 订单数据仍在原 Node `e-commerce/server/sqlite/database.sqlite`：orders 52 单、order_items 77 条，**有下单记录的用户仅 3 个**（user_id=1/2/3，其中 user_id=1 占了 37 项），商品 32 个。
- 结论：用户仅 3 个，KMeans 分 3 簇（每簇 1 人），「分群热销」当前退化为「全体热销过滤已购」；框架已就位，用户/订单积累后分群自然分化。第一版目标「链路通、结果可解释」，不追推荐效果指标。

### 架构（数据流）

- **离线**：训练脚本读订单数据源 → 清洗（排除未支付/已取消订单）→ 算 RFM 特征 → KMeans 分群 → 每簇热销商品。
- **在线**：小程序 → 网关 → product-service `/api/products/recommend` → RestClient 调 Python `/recommend?user_id&limit` → 返回 product_ids → `ProductMapper` 查 products 拼 `ProductVO` → 返回。

### 目录与接口

- Python 项目：`D:\study\my-workspace\wechat-workspace\recommender-python`（推荐服务代码在 `recommend/` 子目录：`main.py` / `recommender.py` / `datasource/{base,sqlite,mysql}.py` / `matrix.py` / `config.py` / `requirements.txt`）。
- Java：product-service 下 `RecommendController` / `RecommendService` / `RecommendServiceImpl`。

接口约定：

- Python `GET /recommend?user_id=1&limit=10` → `{"product_ids":[...],"scores":[...]}`；缺 user_id 或无历史返回热门兜底。
- Java `GET /api/products/recommend?limit=10` → `Result<List<ProductVO>>`；登录态用 `UserContext.getUserId()`，未登录 → 请求 Python 不带 user_id → 走热门。

### 阶段划分

| 阶段 | 内容 | 依赖 |
|---|---|---|
| 1 Python 服务 | FastAPI 骨架 + SQLite 数据源 + RFM 分群 + 群体热销推荐 + 热门兜底，本地 uvicorn 验证 | 原 SQLite 数据（已有） |
| 2 Java 桥接 | RecommendController/Service + RestClient 调 Python + 超时兜底 + 同步 openapi.yaml | 阶段 1 |
| 3 前端 UI | 小程序首页「猜你喜欢」区块 | 阶段 2 |
| 4 数据迁移 | orders/order_items 迁 MySQL（参照 `docs/sql/product.sql` 迁移方式），Python `config.py` 切 `mysql_source` | order-service（task_plan 阶段 4） |

阶段 1 完全不依赖 order-service，现在即可动手；阶段 4 是长期方向，两者解耦。

### 实现状态（2026-10-04）

阶段 9 第 1~3 项已完成并端到端实测跑通：

**Python 服务（`recommender-python/recommend/`，9 个文件）**
- `main.py`（`/health` `/recommend` `/train`）、`segmentation.py`（RFM + KMeans 分群 + 簇内热销）、`recommender.py`（群体热销推荐 + 兜底）、`datasource/`（base/sqlite/mysql，两方法 `load_orders` / `load_products`）。
- 数据源只读原 SQLite，有效下单 22 条（排除 cancel/pending_confirm 后）、在售商品 30 个、3 个用户。
- 冷启动口径定案：**未下单默认按销量降序推荐**，销量 = `sum(quantity)`，覆盖全量在售商品（`listing_status=1`），无销量商品按 id 排后补位。

**Java 桥接（product-service）**
- 新增 `RecommendController`（`GET /api/products/recommend`，免登录走白名单 `/api/products/**`）+ `RecommendService` / `RecommendServiceImpl`（RestClient 调 Python + 超时热门兜底）+ `RecommendResponseDTO`。
- `ProductMapper` / `ProductService` 加 `listOnSaleByIds` / `listProductsByIds`（按 ID 列表查上架商品）。
- application.yml 加 `recommend.base-url/connect-timeout/read-timeout`。
- 端到端实测：未登录返回顺序 `[2,9,6,7,15,1,12,18,19,20]` 与 Python 热门一致，JSON 反序列化（@JsonProperty）正确。

**本地启动**
- Python：`cd recommender-python/recommend && uvicorn main:app --port 9009`
- product-service：`mvn spring-boot:run`（需 Nacos；本地单测可用 `--spring.cloud.nacos.discovery.enabled=false` 绕过）

### 约定/风险

- Python 连 MySQL 的密码走环境变量，不进 git（对齐项目现有凭据规范）。
- 新增 `/api/products/recommend` 后按约定同步 `docs/openapi.yaml`。
- 数据稀疏（3 用户），第一版不追指标；后续数据多了可再加内容相似度（categoryId/tagId）加权融合。

## Redis 缓存（分类树）（2026-10-07）

### 设计定案

- 只缓存**分类**（`GET /api/categories`），商品不缓存（分类页懒加载按父类查商品 `GET /api/products?categoryId=x`）。
- key 结构：
  - `category:parent:ids` → 所有一级父分类 id 数组 `[1,2,3,4,5,6]`
  - `category:parent:{父id}` → 该父分类 + children 子分类，组装成树形 VO（父对象里含 children 数组）
- 查询：读 `category:parent:ids` → `MGET` 各父分类树 → 合并返回；索引或任一父树缺失则整体重建（分类少、变更不频繁，重建最省事）。
- 更新（后台写接口接入时做）：改任意分类 → 找到其一级父 id → `DEL category:parent:{父id}`；增/删一级父分类 → 维护 `category:parent:ids` 数组 + `DEL category:parent:{父id}`。
- `GET /api/categories` 返回结构从扁平列表改为**树形**（`List<CategoryTreeVO>`），前端 `CategoryPanel`/`category.vue` 相应改用 `item.children` 提取二级分类。

### 踩坑点（Spring Boot 4.1 + Jackson 3）

1. **Redis 配置前缀仍是 `spring.data.redis`，但配置类改名 `DataRedisProperties`**（Boot 4 包名 `org.springframework.boot.data.redis.autoconfigure`，不再是旧的 `RedisProperties`）。
2. **Jackson 3 序列化器**：Spring Data Redis 4.1 提供 `GenericJacksonJsonRedisSerializer`（收 `tools.jackson.databind.ObjectMapper`），不是 Jackson 2 的 `GenericJackson2JsonRedisSerializer`（名字多一个「2」，照抄旧资料会编译不过）。
3. **default typing 的坑**：`GenericJacksonJsonRedisSerializer(ObjectMapper)` 构造器**不会自动启用 @class**，直接传 Spring 默认 ObjectMapper 时，序列化不带 @class、反序列化退化成 `LinkedHashMap`（类型丢失）。用 builder 的 `enableDefaultTyping` 才带 @class，但又引入类型校验：`ProductVO` 的 `BigDecimal`（非 final）会写 @class，若 validator 只允许 `java.util.`，反序列化 `java.math.BigDecimal` 报 500。
4. **最终方案（按用户要求）**：不用 @class，改 `StringRedisTemplate` + `ObjectMapper` 手动序列化成**纯 JSON**，反序列化显式指定类型（`readValue(json, CategoryTreeVO.class)`），删除自定义 RedisConfig（含 default typing）。

## 订单域迁移 + 购物车后端（2026-10-08）

> 从原 Node 的 SQLite 迁移订单域 4 张表到 MySQL，并在 order-service 实现小程序购物车后端。

### 1. 订单域 4 表迁移

- `docs/sql/order.sql`：建表（orders / order_items / cart / consignee_address），只建表不落数据，幂等。
- `docs/sql/order-data.sql`：数据迁移（52 订单 / 76 订单项 / 1 购物车 / 15 地址），INSERT IGNORE 幂等。
- 迁移规则延续 product.sql：BIGINT AUTO_INCREMENT、Unix 秒 → DATETIME（FROM_UNIXTIME）、REAL → DECIMAL(10,2)。

### 2. 关键决策

| 决策 | 结论 |
|------|------|
| 排序规则 | 新表显式 `COLLATE utf8mb4_general_ci`（服务器 MySQL 5.7 不支持 8.0 的 0900_ai_ci，导出导入会失败）；旧的 product/admin 表及已建表用户决定不管 |
| 时间字段命名 | 订单域用 `created_at/updated_at`（忠于原表 _at 风格，与 pay_at/confirm_at 统一）；与商品域 create_time/update_time 有意不同 |
| 外键 | 加物理外键含 user_id → user.id（orders RESTRICT、cart/address CASCADE）；order_items.product_id 不加（订单项是快照 + 孤儿数据） |
| user_id 映射 | 原 sqlite user_id=1/2/3 是后台账号表（admin/user/user2），与小程序 user 表不同体系 → 插 3 个占位用户（显式 id=5/6/7），订单/购物车/地址 1:1 映射 |

### 3. 踩坑点

- **TEXT 列不能带字面量默认值**：`insurance_detail TEXT DEFAULT '[]'` 报 `ERROR 1101` → 改 `VARCHAR(512)`。
- **孤儿数据**：order_items 有 1 条 `product_id=41` 指向已删商品，用户决定丢弃；随后发现 sqlite 已被重新导出、该条已消失（77→76 条）。
- **orders.id 不连续**（8~100 跳号）：保持原 id 迁移，order_items.order_id 无需改。
- **中文乱码**：迁移脚本用 Python 读 sqlite 直接写 UTF-8 的 INSERT SQL（绕过终端 GBK），导入加 `--default-character-set=utf8mb4`。

### 4. 购物车后端（order-service）

- 5 接口照搬原 Node：`GET /api/cart`、`POST /api/cart`、`PUT /api/cart/{productId}`、`DELETE /api/cart/{productId}`、`DELETE /api/cart`（清空，可选 body.productIds 批量删）。
- 快照来源：**同库直接查 products 表**（用户选），加购时后端查 name/image/price/description 落快照，不信任前端传的价格。
- 鉴权：`/api/cart/**` 不在 `EXCLUDE_AUTH_PATHS` 白名单，`JwtInterceptor` 自动要求 user 令牌。
- 包结构：`ecommerce.order` 下分 controller/service/mapper/entity/dto/vo，`@MapperScan("ecommerce.order.mapper")`。
- gateway 补路由 `/api/cart/** → order-service`（否则落到 `/api/**` 的 user-service 兜底）。
- 编译通过（BUILD SUCCESS）；未启动端到端验证（用户选择先不测）。

## 订单功能实现 + Docker MySQL（2026-10-08）

### 1. 订单状态数字枚举 + 状态机

- `OrderStatusEnum`：6 状态数字 code（1待付款 2待确认 3待发货 4已发货 5已完成 6已取消），照原 Node orderStatus.js。
- 状态机：pending_pay → pending_confirm → pending_ship → shipped → finished，前三态可 cancel；`canTransition` 强校验非法流转。
- `order_status` 字段从 VARCHAR 字符串迁到 TINYINT 数字（`docs/sql/order-status-migrate.sql`：临时列映射 + 换列）。
- **迁移已于 2026-10-09 执行**（打 Docker `127.0.0.1:3307`）：自检列类型 = `tinyint`，分布 `2→10 / 3→4 / 4→2 / 5→11 / 6→25`，与迁移前英文值一一对应（pending_confirm 10 / pending_ship 4 / shipped 2 / finished 11 / cancel 25）。
  - 迁移前存在**代码与库不一致**：`OrderStatusEnum` 与 Mapper 全按 `Integer` 读写，库里列却是 `varchar(32)` 英文值 → 读老数据会 `NumberFormatException`；本次一并消除。
  - **本机 3306 未迁**（回退库，保持英文原样）；只迁了服务实际连接的 3307。
  - 脚本非幂等，重复执行在「加临时列」处报 `Duplicate column`；`CASE` 的 `ELSE 1` 兜底（未收录值/NULL → 1待付款），执行前已确认库中 52 行均为已知值。
  - ⚠️ 同类遗留：`orders.sign_status` 仍是 varchar 英文（`signed`/`unsigned`），`tags.type` 是 varchar（`custom`），未迁。

### 2. 订单功能（order-service）

- 8 接口：下单/列表/详情/支付/确认收款/发货/完成/取消。
- 下单：查商品 → 校验库存 → 扣库存（乐观锁 `stock >= quantity`）→ 写订单+明细（初始待付款），`@Transactional` 保证原子性。
- 状态流转自动记时间戳：支付记 pay_at、确认记 confirm_at、发货记 ship_at+单号、完成记 finish_at+签收（OrderMapper.xml 动态 SQL）。
- 超时关闭：`@Scheduled` 每分钟扫，`created_at` 超 30 分钟（1800s）的待付款订单恢复库存+改 cancel（**不软删除**——待付款订单是有效记录，超时只是状态流转，保留供用户查看），**二次确认**防与支付并发误取消。
- 库存：下单扣、取消/超时恢复（restoreStock）。

### 3. Docker 部署 MySQL

- `docker-compose.yml` 启用 mysql 服务：镜像 8.0，端口 3307 避开本机 MySQL80，字符集 utf8mb4_general_ci，命名卷 mysql-dev-data 持久化，restart: unless-stopped。
- 数据从本机 mysqldump 迁移（9 张表；admin 表两边都缺——admin.sql 从没执行过）。
- 4 个服务的 application-dev.yml 数据库连接 3306 → 3307。
- 本机 MySQL80 仍在 3306（可回退）。
- ⚠️ **3306 已成落后库（2026-10-09 起两库分叉）**：3307 后来新增 `user.is_deleted`、把 `orders.order_status` 从 varchar 迁成 tinyint，**3306 两者都没有** → 用现在的代码连 3306 会 `Unknown column 'is_deleted'`、读旧 `order_status` 抛 `NumberFormatException`。日常一律连 **3307**。
  - **IDE（IntelliJ）数据源也必须指向 3307**：指向 3306 时，`CartMapper.xml` 报「Unable to resolve table 'cart'」、`UserMapper.xml` 报「Unable to resolve column 'is_deleted'」——这是 **IDE 元数据过期**（连的是缺列/缓存旧的 3306），**不是代码或库的问题**；切 3307 并 Refresh 后消失。

## 购物车实现盘点（2026-10-09）

> 只读盘点：核对 order-service 购物车实际代码，确认已实现范围与缺口。**本次未改任何代码**。

### 已实现（事实）

- 5 接口（`CartController`，`/api/cart`）：列表 `GET` / 加购 `POST` / 改数量 `PUT /{productId}` / 移除 `DELETE /{productId}` / 清空+批量删 `DELETE`（body 可选 `productIds`）。
- 分层齐全（均在 `order-service` 的 `ecommerce.order` 包）：`controller/CartController`、`service/CartService(+impl/CartServiceImpl)`、`mapper/CartMapper(+resources/mappers/order/CartMapper.xml)`、`entity/Cart`、`entity/Product`+`mapper/ProductMapper(+XML)`、`dto/AddCartDTO`+`UpdateQuantityDTO`+`ClearCartDTO`、`vo/CartVO`。
- 表：`docs/sql/order.sql:80` 的 `cart` 表，唯一键 `uk_cart_user_product(user_id, product_id)`（一人一商品一行）、外键 user_id→user / product_id→products（ON DELETE CASCADE）。
- 鉴权：`/api/cart/**` 不在 `EXCLUDE_AUTH_PATHS` → `JwtInterceptor` 自动要求 user 令牌，用户 id 从 `UserContext.getUserId()` 取（前端不传 userId，防越权）。
- 网关：`gateway/application.yml:80` `/api/cart,/api/cart/** → lb://order-service`（排在 `/api/**` user-service 兜底之前）。

### 与 Node 原版的关键差异

- Node `cartController.addItem` **信任前端传入的** `name/image/price/description` 直接落快照；Spring 版改为**后端查 products 表**（`ProductMapper.findById`）生成快照，**不信任前端价格**——防篡改，是刻意的改进。
- 其余 5 接口路径/语义照搬 Node（与 `routes/cartRoutes.js` 对照一致）。

### 还缺 / 要注意

| # | 项 | 现状 | 影响 / 补法 |
|---|---|---|---|
| 1 | **下单不清购物车** | `OrderServiceImpl` 完全未引用 cart | 下单成功后购物车项不会自动移除；若要「下单即移除」需在下单事务里补 `cartMapper.deleteByProducts(...)` |
| 2 | **加购不校验库存/上架** | `ProductMapper.xml` 的 `findById` 只按 `id + shop_id` 查，无 `stock` / `listing_status` 过滤 | 允许加购下架/无货商品；库存校验只在下单时做。是否加购即拦取决于产品口径 |
| 3 | **未端到端验证** | 5 个接口未实际启动 order-service 测过 | 存在运行时未知数（用户当时选择先不测） |
| 4 | **前端未接** | uniapp `api/` 只有 categories/login/product，**无 cart api 模块**；`pages/cart/cart.vue` 未调接口 | 前端这一层待做（`cart.vue` 里没有任何 api 调用） |
| 5 | **多租户固定模板店** | `CartServiceImpl.TEMPLATE_SHOP_ID = 1` 硬编码 | 沙箱隔离接入前按模板店跑 |
| 6 | `insurance_detail` / `insurance_fee` | 固定 `"[]"` / 0（历史遗留） | 小程序未启用，通常为空，可忽略 |

### 文档约定变更（顺带核实）

- **手写的 `docs/openapi.yaml` 已不存在**：2026-10-05 提交 `8228496` 删除（525 行），改为 springdoc 按 `@Schema` / `@Operation` / `@Tag` 注解**自动生成**，入口见新增的 `docs/swagger-addresses.md`（网关聚合 `http://localhost:8080/swagger-ui.html`）。
- 因此本文档「项目架构约定」里「接口改动必须手工同步 `docs/openapi.yaml`」一条**已过时**——现在只需保证注解齐全，文档自动出。**遗留：该节其余处仍按 openapi.yaml 描述，尚未修订。**

## 用户软删除（user.is_deleted，2026-10-09）

### 背景

`orders.user_id` 是 `ON DELETE RESTRICT`（订单不随用户删），而 `user` 表原先**没有**任何生命周期字段 → 「用户注销」无处落地：删不掉也标不了。故给 `user` 加软删除字段。

### 落地

- **表**：`ALTER TABLE user ADD COLUMN is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除：1 已删除 0 正常'`（AFTER avatar）。命名/类型对齐 `orders.is_deleted`，遵循「状态类字段存 TINYINT 0/1，不存英文」。
- **实体**：`User.isDeleted`（`Integer`）。`select *` + `spring` 的 `mybatis.configuration.map-underscore-to-camel-case: true` → `is_deleted` 自动映射，**无需手写 resultMap**。
  - 用 `Integer` 而非 `boolean`：Lombok 对 `boolean isDeleted` 会生成 `isDeleted()/setDeleted()`（getter 名与字段名不一致），MyBatis 反射映射会踩坑；`Integer` 生成 `getIsDeleted/setIsDeleted`，映射正常。
- **判断**：`UserServiceImpl` 加私有 `isDeleted(user)` / `findActiveUser(userId)`；登录时已注销 → 403「账号已注销」，`getProfile`/`updateName`/`uploadAvatar` 统一走 `findActiveUser`（已注销 → 401）。

### 关键坑：注销不能只靠 SQL 过滤（uk_openid）

`user.openid` 有 `UNIQUE KEY uk_openid`。注销只是把行标记 `is_deleted=1`，**该行仍在、openid 仍被占用**。所以：

- **`findByOpenid` 不能加 `and is_deleted = 0` 过滤**：一旦过滤，同一微信号再登录会「查不到 → 走首次注册 insert → 撞 uk_openid 报错」。
- 正解：查询照常返回该行，**由 Service 判 `is_deleted` 拒绝登录**（已实现）。若将来要允许注销用户重登，得在注销时把 `openid` 置空/改写，而不是靠过滤。

### 注销接口（2026-10-09 实现）

- `POST /api/user/deactivate`（需登录，取 `UserContext.getUserId()`）→ `UserServiceImpl.deactivate`：
  `@Transactional` 内 ① `findActiveUser` 校验（已注销 → 401，重复注销在此被挡）② `UPDATE user SET is_deleted=1` ③ `DELETE FROM cart WHERE user_id=?`。
- **为什么由 user-service 直连同库删 cart**：cart 表归 order-service，但要让两步**同一事务**，只能由同一服务用同一连接执行（跨服务调用无原子性，Seata 尚未引入）。沿用 order-service 直查 products 的同库直连先例；将来拆库 / 上 Seata 再改。
- user-service 新增 `mapper/CartMapper`（+ `mappers/user/CartMapper.xml`），是 user-service 里唯一碰 cart 表的地方。
- **订单（39 条）与收货地址保留**：订单是财务数据（`RESTRICT` 的意义所在）；地址快照已冗余进订单，可留可清，本次不动。
- 路由：`/api/user/deactivate` 未被 auth-service 的精确路径（`/api/user/info`）匹配，落到 `/api/**` → user-service，**网关无需改**。
- ⚠️ 语义澄清：**软删除（UPDATE）不会触发 FK CASCADE**——CASCADE 只在父行被物理 DELETE 时生效。所以注销后 cart/address 不会自动跟删，必须由代码显式清（本接口做的就是清 cart）。

### 未做（遗留）

- 无后台 admin 侧的「注销指定用户」接口（当前只有 C 端自助注销）。
- `consignee_address` 未清（可留）。

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

## 购物车增值保障 + 库存校验（2026-10-11）

购物车从「占位」补齐成完整链路，几个关键设计点：

### 增值保障数据来源：前端加购弹窗，不是商品表

- 前端 `ProductPopup` 三个可选保障项（全面保修 1%、一年意外保 2%、电池无忧 ¥20），多选后随 `addToCart` 的 `insuranceDetail`（数组）+ `insuranceFee`（单件合计）传后端；后端 `AddCartDTO` 接收、`addItem` 快照进 `cart.insurance_detail`（JSON 串）+ `insurance_fee`。
- 一度想在 `products` 表加 `insurance_fee` 固定值，但增值保障是多选、按售价比例计费，单值覆盖不了 → 字段加又删，最终回退。

### subTotal 只读派生，不落库

- `CartVO.subTotal = (price + insuranceFee) × quantity`，后端 `calcSubTotal` 实时算。
- 落库要同步维护 price/quantity/insuranceFee 三处变更，成本高易脏，故只读派生。

### 库存上限：实时 join + 后端权威校验

- `CartMapper.listByUser` `left join products` 带出 `stock`，`Cart`/`CartVO` 加 stock。
- `addItem`（已有 + 本次 ≤ 库存）、`updateQuantity`（目标 ≤ 库存）超了抛 400「库存不足，当前仅剩 X 件」。
- 前端 `QuantityStepper` 加 `max` prop，输入/加号超限即时 toast；后端仍兜底（商品下架 stock null → 兜 0）。

### 商品副标题重写

- `products.sub_title` 全部改成「商品名 + 卖点」≤35 字（`docs/sql/product-subtitle.sql`，已执行），加购弹窗显示 subTitle。

## 收货地址模块（2026-10-11）

### 表：consignee_address 改名 address

- 订单域已有 `consignee_address` 表（含 user_id 索引 + user_id 外键 + 历史数据），收货地址模块直接复用、改名 `address`。
- `region` / `detail_address` 原为 NULL（从原 sqlite 系统原样迁移），改成 NOT NULL（不填无法发货）。
- 注意：数据库里外键名还是 `fk_consignee_address_user`（改名时未同步改），order.sql 文档已写 `fk_address_user`，命名有出入但功能无影响。

### 默认地址设计

- 不单独提供 GET /address/default 接口，列表全量返回，前端拿 is_default=1 那条做默认。
- 唯一性靠 Service 层事务：新增/修改设默认前先 clearDefault（清 0 其他），第一条地址自动设默认；删除默认地址后 setDefaultToLatest 自动把 id 最大一条设为默认。
- 「设为默认」通过 PUT /api/address/{id} 改 is_default=1 实现，不单独开接口。

### 省市区级联

- 前端用微信原生 `picker mode="region"`（数据微信内置、自动级联、零包体积），region 存「省 市 区」字符串。
- 不拆 province/city/district 三字段、不建省市区表：收货地址只是展示 + 发货，结构化查询（按省筛选/运费模板）才需要拆。

## 视觉/浏览器发现

- 本次探索读了 Node 后端（`e-commerce/server`）与前端（`e-commerce`）的本地代码与配置，并用 WebSearch 确认了 Spring Cloud Alibaba / Nacos 对 Spring Boot 4 的版本兼容（见本节「微服务版本兼容」）。

---
*每执行2次查看/浏览器/搜索操作后更新此文件*
*防止视觉信息丢失*
