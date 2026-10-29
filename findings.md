# 发现与决策

> 本文件只存事实与研究结论。外部网页/搜索内容只写这里，不写进 `task_plan.md`。

## 本地启动流程（开发环境）

> 2026-10-03 补记。起因是一次排查：`java.exe` 只有 IDEA、8080/8848 全无监听，结论是**后端不是启动失败，是根本没起**；而且即使去起，Docker 没开的话也起不来。

### 核心机制：必须先 Nacos，再服务

所有服务在启动阶段都要向 Nacos 注册（各 `application.yml` 里的 `spring.cloud.nacos.discovery.server-addr`，默认 `${NACOS_ADDR:127.0.0.1:8848}`）。
Nacos 只有一份，是 Docker 容器（根目录 `docker-compose.yml`）。**注意**：该文件里仍保留 `mysql` 服务（映射宿主 3307），但 2026-10-18 起 MySQL 已统一用本机 3306，开发期不需要这个容器；只想起 Nacos 用 `docker compose up -d nacos` 即可（compose 文件注释尚未清理）。

**Docker 没起 → Nacos 没起 → 8848 无监听 → 服务注册失败、启动不成功。**

容易踩的误解：「只把 application 跑起来就行」——不行，注册中心不在，服务起不来。

对照：**MySQL 走本机 MySQL80（3306）**；2026-10-18 已把 Docker 3307 的数据迁回本机 3306，不再用 Docker 部署 MySQL。

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

> ⚠️ 不起 Redis 的话，`GET /api/categories`（分类树）会先等约 2 秒连接超时再降级查库——`listCategories()` 已加 try/catch 回源，不会 500，但会变慢；商品列表/详情接口不依赖 Redis，不受影响。

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

| 服务 | 端口 | 启动类 | 需要环境变量 |
|---|---|---|---|
| gateway | 8080 | `GatewayApplication` | 否 |
| auth-service | 9001 | `AuthServiceApplication` | **是**（DB） |
| product-service | 9003 | `ProductServiceApplication` | **是**（DB） |
| order-service | 9004 | `OrderServiceApplication` | **是**（DB） |
| aftersale-service | 9005 | `AftersaleServiceApplication` | **是**（DB） |
| user-service | 9006 | `UserServiceApplication` | **是**（DB/微信） |
| file-service | 9007 | `FileServiceApplication` | 否（DB） |
| ai-service（Python） | 8000 | `uvicorn app.main:app --port 8000` | **是**（DB 只读/Redis/AI key） |
| Nacos | 8848 / 9848 / 9849 | — | — |
| Nacos 控制台 | 18080（容器内 8080） | — | — |
| MySQL | 3306（本机 MySQL80） | — | — |
| Redis | 6379（本机原生，非 Docker） | — | — |

> 除 gateway 外的所有 Java 服务都依赖 `common`，启动时还会校验 `JWT_SECRET`（`JwtProperties` 非空）；缺了直接启动失败。

### 已知坑：jar 可能落后于源码

用 jar 方式启动前先全量重打一次，避免 `target/*.jar` 落后于源码（凭据走环境变量，不打包进 jar）：

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
| 启动报 `DB_USERNAME` 占位符解析失败 | `DB_USERNAME` 环境变量没设（凭据清单见 `docs/env.md`） |
| `GET /api/categories` 返回慢（约 2s） | Redis 没启动（分类树缓存依赖 Redis）；代码已降级回源，不会 500，起 Redis 即可恢复毫秒级 |

## 项目架构约定（原 `project_spec.md`）

> 2026-09-28 由根目录 `project_spec.md` **全文并入**本节，原文件已删除（见 `progress.md` 同日会话）。
> 微信小程序、PC 端商城（网页版）、PC 端后台管理系统（Vue）三个前端共用一个后端（本项目）。本文记录模块划分与命名规则，后续开发照此执行。

### 核心原则

按 **业务模块** 建包，不按「端」建包。每个业务模块内部「数据共用、接口分层」。

> 现状（2026-10）：项目已拆成 `user-service`/`product-service`/`order-service`/`aftersale-service` 等独立 Maven 模块，但**服务内部**仍遵循本节约定 —— 按业务模块建包，包内分 `controller/service/mapper/entity/dto/vo`，C 端 `User*` / 后台 `Admin*` 前缀保留；原 `goods` 域对应现在的 `product-service`。

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

### 接口文档（springdoc 自动生成）

> 手写静态契约 `docs/openapi.yaml` 已删除；接口文档以 springdoc 运行时生成 + 网关聚合 Swagger UI 为准（`gateway/application.yml` 的 `springdoc.swagger-ui.urls` 聚合各服务文档）。以下注解约定仍然有效。

- **响应结构**：统一按 `Result<T>` 的实际字段 —— `success` / `data` / `message` / `code`；HTTP 状态码真实返回（成功 200，失败 400/401/403/404/500），不要沿用旧的 `code`/`msg`、HTTP 恒 200 的写法。
- **要不要带令牌怎么声明**：根级 `security: - bearerAuth: []` 表示全文档默认要带令牌，登录接口用 `security: []` 单独关掉
  - `commonFailureResponsesCustomizer` 按 `EXCLUDE_AUTH_PATHS` 自动补这个覆盖，Swagger UI 上登录接口不显示锁图标
  - 令牌方案 `bearerAuth`（`type: http` / `scheme: bearer` / `bearerFormat: JWT`）在 `components.securitySchemes` 定义一次
  - **`security` 不产生 401/403 响应**：它只声明「需要哪种鉴权」，这两件事互不相干，别指望写了 `security` 就不用管失败响应。
- **公共失败响应全局注入，Controller 上不写任何 `@ApiResponse`**：`OpenApiConfig#commonFailureResponsesCustomizer` 按下面的规则统一注入，并把所有失败响应体统一成 `ResultVoid`。
  | 码 | 注入范围 | 产出方 |
  |---|---|---|
  | `400` | 所有接口 | `GlobalExceptionHandler#handleUnreadable` |
  | `401` / `403` | 需登录接口（`/api/**` 且不在 `EXCLUDE_AUTH_PATHS`） | `JwtInterceptor` |
  | `500` | 所有接口 | `GlobalExceptionHandler#handleOther` 兜底 |
  | `413` | 只有带 `multipart/form-data` 请求体的接口 | `GlobalExceptionHandler#handleMaxUploadSize` |

  - **判据是「这个码由谁产出」，不是「看起来像不像业务码」**：凡是 `GlobalExceptionHandler` 统一产出的就是公共码，一律全局注入
  - 只有该接口**独有语义**的码才值得本地写注解；按此判据，目前没有任何接口需要本地注解
  - `uploadAvatar` 的 413/500 曾被误当业务码留在本地是错的：413 出自全局上传超限处理器，500 出自全局兜底分支
  - 用的是 `GlobalOpenApiCustomizer` 而不是 `OpenApiCustomizer`：后者按 `GroupedOpenApi` 分组各跑一次，将来分了组会把响应重复注入。
  - 已在本接口显式声明过的码**不会**被覆盖（`containsKey` 守卫），留作将来真有接口独有码时的逃生口。
  - **413 只注入带 `multipart/form-data` 的接口**：不筛的话 `wxlogin` 这种根本不能上传的接口也会挂上 413。所以这条规则依赖下一个 bullet 的 `consumes` 声明，两者是配套的。
- **上传接口必须声明 `consumes`**：`@PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)`
  - 不写的话 springdoc 套用默认 `application/json`，文档请求体变 JSON，Swagger UI 给 JSON 编辑器而非文件选择框
  - 同时 413 注入规则失效（判不出是上传接口）；声明后非 multipart 返回 415，是预期行为
- **公共码无需手写**：上表的 400/401/403/413/500 全部由 `commonFailureResponsesCustomizer` 注入，失败响应体统一为 `ResultVoid`；新增接口不加注解也会自动带上
- **写 `@ApiResponses` 的两个坑**（当前代码里已无任何本地注解，但将来真要写时必须知道）：
  1. 方法上一旦标了 `@ApiResponses`，springdoc 就**不再自动补 200**，成功响应必须显式写 `@ApiResponse(responseCode = "200", useReturnTypeSchema = true)`，否则 Swagger UI 里该接口只剩失败码。零注解的接口才享受自动 200。
  2. 手写的失败码如果不给 `content`，springdoc 会拿方法返回值类型去填响应体（把 400 的 `data` 写成头像 URL）。所以失败响应体一律交给 `commonFailureResponsesCustomizer` 纠正，注解里不要自己写 `content`。
- **免登录清单只有一份**：`WebMvcConfig.EXCLUDE_AUTH_PATHS`。拦截器豁免和文档补全都读它（同一套 Ant 匹配语义，支持 `/api/products/**` 前缀豁免），新增免登录接口（登录入口、C 端商品浏览等）只改这一处。
- **运行时文档**：网关聚合入口 `/swagger-ui.html`，各服务 `/v3/api-docs`；`SPRINGDOC_ENABLED=true` 时开启，无需手工维护静态契约。

### 头像上传遗留待办

> 头像上传（`POST /api/user/avatar`）核心闭环已完成：**库存相对路径、返回时拼完整 URL**（换域名不失效）。
>
> 2026-09-27 完成前两项：
> - **图片类型白名单**：`ALLOWED_IMAGE_TYPES` 只收 png/jpeg/gif/webp，挡掉可内嵌脚本的 `image/svg+xml`
>   - 白名单同时给出规范后缀，修掉旧写法从 Content-Type 切出 `svg+xml` 非法扩展名的问题；比对前归一化 Content-Type（小写、去 `;` 后参数）
> - **上传大小限制**：`spring.servlet.multipart.max-file-size: 2MB` / `max-request-size: 3MB`，配在 `application.yml`（非密配置）。超限由 `GlobalExceptionHandler` 转成 413 + 可读提示。
>   - ⚠️ **修正一处旧判断**：Spring Boot 4 / Spring Framework 7 的 `MaxUploadSizeExceededException` 本身实现 `ErrorResponse`
>     - 不接也返回 413（只是提示「请求不合法」），不是旧文档写的「超限返回 500」；单独接它是为了给出可读提示
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
| 后台 Admin 端以驼峰为主（订单/库存/用户统计等），商品模块保留蛇形（`@JsonNaming(SnakeCase)` 兼容旧 Node 前端），分页 `Result<PageVO<T>>` | 商品前端照旧 Node 蛇形契约写、改动成本高；其余模块用驼峰消除翻译层，见下节「后台 Admin 端对接契约」 |

## 后台 Admin 端对接契约（2026-10-19）

> 后台管理端（`e-commerce` Vue3 前端）对接 Spring 后端的统一契约，从商品 CRUD 确立，后续分类/标签/库存/订单/售后后台对接都照此做。

- **路径**：后台写操作走 `/api/admin/**`，`JwtInterceptor` 按此分流 admin 身份（复用 `/api/products` 会撞免登录白名单 + admin 令牌被判成 user 403）。
- **字段以驼峰为主，商品模块例外**：订单/库存/用户统计等 VO（OrderVO/StockVO/DashboardVO）是驼峰（Jackson 默认，无注解）；但商品模块 AdminProductVO/AdminProductDTO 用了 `@JsonNaming(SnakeCase)` 返回蛇形。对接时按具体模块确认字段名，别一刀切（工作台订单统计曾因前端按 snake_case 读驼峰的 OrderVO 导致数据全空，2026-10-20）。
- **分页**：返回 `Result<PageVO<T>>`（`PageVO` 在 common，字段 list/total/page/pageSize），前端读 `res.data.data.list` / `res.data.data.total`，`pageSize` 从 `res.data.data.pageSize` 读（后端返回实际值，前端不写死 pageSize）。订单列表 4 个状态 tab 独立请求后端分页（status+page，pageSize 后端默认 13）；发货页 3 tab 全量（status 筛选 + pageSize 给足，订单量少不分页）。
- **单店模式（2026-10-29）**：多租户已下线，JWT 不再签 `shopId`，service 不再按店铺过滤。
- **时间字段**：后台 VO 用 `createdAt`/`updatedAt`（entity 是 createTime/updateTime，toVO 映射），前端读 createdAt/updatedAt。
- **时间格式**：`LocalDateTime` 加 `@JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")`，避免默认 ISO 格式（T 分隔）前端不好读。
- **日期筛选**：列表接口收 `dateField`/`startDate`/`endDate`，`dateField` 走白名单（created_at/confirm_at/pay_at/ship_at/finish_at）防动态列 SQL 注入；前端公共组件 date-range-select 统一吐这三个参数。
- **售后审核**：`PUT /api/admin/after-sales/{id}` 只改主表 `status/merchant_remark/updated_at`，业务时间戳按状态写对应子表列（STATUS_COLUMN_MAP 白名单：refunding/returning→review_at、rejected→rejected_at、refund_confirming→refund_at、refunded→refunded_at、exchanging→exchange_at、exchanged→exchanged_at）；删除=主表/明细/子表 `is_deleted=1` 软删。售后前端字段蛇形→驼形对齐（afterSaleNo/createdAt/afterSaleType/payTypeDesc 等）。

## 单店化改造（2026-10-29）

多租户沙箱彻底下线，退化为单店。关键事实：

- **代码**：删除 `shop-service` 模块、网关 `/api/admin/shops/**` 路由与 Swagger 条目、compose/release 项；`JwtUtil` / `JwtInterceptor` / `UserContext` 去掉 shopId；各服务实体、Mapper、XML、Service 全部去掉 `shop_id`。
- **WebSocket**：`WebSocketSessionRegistry` 由 `Map<shopId, Set>` 改为单个 Set，`sendToShop` → `broadcast`；`AuthHandshakeInterceptor` 只校验 admin 类型，不再要求 shopId。
- **数据库**：18 张表删 `shop_id`，`user.uk_shop_openid` → `uk_openid(openid)`，`product_favorites.uk_shop_user_product` → `uk_user_product(user_id, product_id)`，`shops` 表删除。迁移脚本 `docs/sql/single-shop-01-cleanup.sql`（发布前清沙箱数据）、`single-shop-02-drop-shop-id.sql`（全部新代码上线后删列），说明见 `docs/sql/README.md`。
- **AI 服务**：`recommender-python` 也查 `products.shop_id`，已同步删除，且必须在 02 DDL 前发布，否则导购查询报 `Unknown column`。
- **发布顺序**：01 清理 → product → order → aftersale → user → ai → 后台前端 → gateway → **auth 最后**（auth 停止签发 shopId，旧服务会拒绝新 token）。`deploy/release.sh` 的 `ALL_SERVICES` 已把 auth 排到最后。
- **兼容**：旧 token 带 shopId 可忽略；旧小程序多传 `shopId` 由 `WxLoginDTO` 的 `@JsonIgnoreProperties(ignoreUnknown = true)` 忽略。

## 多租户沙箱（2026-10-20，已废弃）

> 2026-10-29 已彻底下线，以下仅作历史记录。

- **开沙箱**：`POST /api/admin/shops/sandbox`（2026-10-28 起需 admin 令牌；前端先走后台账号「手机号 + 验证码」登录，再开沙箱；原 `/api/shops/sandbox` 已删除并移出免登录白名单），shops 插行 → 15 张业务表按「id + offset」克隆模板店数据（offset = shopId × 100 万，MySQL 全局自增显式指定 id 后 AUTO_INCREMENT 跳大，无害）→ 签发沙箱店 admin 令牌。admin 账号也克隆：username 加 `_{shopId}` 后缀（如 `admin_16`）、password 随机（一键体验直接签 token，不走密码登录）。
- **克隆唯一键冲突（2026-10-20 修复）**：`orders` 表有 `uk_orders_order_no` 唯一键，`cloneOrders`/`cloneAfterSales` 的 order_no/after_sale_no 原样复制会撞唯一键 → 开沙箱 500。改成 `CONCAT(order_no, '_s', shopId)` 后缀（与 cloneTags.code、cloneAdmin.username 同一套做法）。模板店一开始没订单时不暴露，有订单后才炸。
- **C 端多租户**：`user` 表加 `shop_id`，唯一键 `uk_openid` → `uk_shop_openid (shop_id, openid)`；C 端 user 令牌签 shopId（`JwtUtil.generateUserToken(userId, shopId)`），`JwtInterceptor` 已通用解析 shopId 写入 `UserContext`（不用改）；order-service C 端下单/支付/购物车/地址从写死模板店改成 `UserContext.getShopId()`。
- **小程序端**：`SHOP_ID` 常量（默认 1）登录传 shopId；扫码进店/分享链接暂不做。
- **user 表重复用户（2026-10-27）**：`cloneUser` 每次开沙箱整表复制模板店 user（含真实 openid/手机号/头像），而「过期回收」一直没做 → `user` 表按店铺数叠加重复 openid。小程序 C 端固定 shopId=1，这些行不是小程序写入的，只是克隆数据。
- **user 不能跳过克隆（2026-10-27）**：`orders.user_id` / `address.user_id` / `cart.user_id` 都有外键指向 `user.id`（orders 是 RESTRICT），不复制 user 会导致克隆订单插入失败；只能克隆行、脱敏字段。
- **克隆脱敏（2026-10-27）**：openid → `sb_{shopId}_{id}`（列宽 varchar(45) 够用）、name → `体验用户{id}`、phone/avatar → NULL，sex 保留。避免生产库出现真实用户 PII 副本。
- **过期回收（2026-10-27）**：shop-service 加 `@EnableScheduling`，`cleanExpiredSandboxes()` 每小时（fixedDelay 1h，启动即跑一次）取 `is_template=0 AND expires_at < now`（阈值用 Java 时间传入，MySQL 服务器是 UTC 不能用 NOW()），每批 20 个。删除顺序按外键：售后子表 → after_sales → order_items → orders → cart → feedback/product_favorites/product_reviews（不克隆但带 shop_id，顺手清）→ address → user → admin → stock → products → tags → categories（自引用 CASCADE）→ shops。整批一个事务，失败回滚下次重试。
- **沙箱 token 与店铺同寿命（2026-10-27）**：`JwtUtil.generateAdminToken` 加 `expireSeconds` 重载（原 4 参方法仍用 `jwt.expire` 7 天，账号密码登录不受影响）；`createSandbox` 用 `Duration.between(now, shop.expiresAt)` 算 TTL（≈24h，克隆耗时已扣掉，兜底 60s）。店过期后所有 `/api/admin/**` 返回 401，前端拦截器清 token 回登录页，不再出现「店已删、token 还能用、页面停在空数据」的空窗。
- **前台到期强制登出（2026-10-27）**：`utils/shop.js` 拉完店铺信息按 `expiresAt` 挂 `setTimeout`（字符串显式按 `+08:00` 解析），到点清 token、断来单 WS、带提示跳登录页（定时器负责空闲页面，后端 401 是兜底）；`router/permission.js` 导航前解 JWT `exp`，过期直接回登录页，避免刷新时闪后台。
- **沙箱复用（2026-10-28）**：原逻辑每次点「一键体验」都新开一家店，退出再登录 username 就变（`admin_{shopId}`），一天反复登录会把 shops/admin/user 撑爆。现在前端 `utils/sandboxVisitor.js` 在 localStorage 持久化 UUID（退出登录/401/到期都不清），请求头 `X-Sandbox-Visitor-Key` 带过去；`shops` 加 `visitor_key` + 唯一索引（迁移脚本 `docs/sql/sandbox-visitor-key.sql`）。后端命中同 key 且剩余 >60s 的沙箱就直接重签原 admin 令牌（`restored=true`），不插店不克隆；已过期/剩余不足 60s/缺 admin 则解绑该 key 后新建。并发同 key 用唯一索引兜底：`createSandbox` 用 `TransactionTemplate` 包建店事务，冲突方事务回滚后在独立连接读胜出店铺复用，不用 `FOR UPDATE`（两个失败方同时抢行锁会死锁）。
- **历史沙箱不回填**：加列前的 35 家沙箱 `visitor_key` 为 NULL，不会被复用，由过期回收自然清掉。
- **admin.create_time 修复（2026-10-28）**：`cloneAdmin` 原来把模板店管理员的 `create_time/update_time` 原样复制，导致所有沙箱 admin 的创建时间都是模板店那条（2026-10-17），按时间统计会完全失真。改为 Service 把开店时刻 `now` 传进克隆 SQL。

## 后台来单提醒 WebSocket（2026-10-20）

- **端点挂 order-service**（非独立推送服务）：`/ws/order`，用 Spring 原生 `WebSocketHandler` + `HandshakeInterceptor`（不用 JSR-356 `@ServerEndpoint`），网关加 `/ws/**` 路由转发。
- **握手鉴权**：浏览器 `new WebSocket` 不能带 header，token 走 `?token=` query，`AuthHandshakeInterceptor` 用 `JwtUtil.parseToken` 只校验 admin 类型。
- **会话注册表（单店模式，2026-10-29）**：`WebSocketSessionRegistry` 从 `Map<shopId, Set>` 改为单个 `Set<WebSocketSession>`，`sendToShop` → `broadcast`。
- **触发点**：支付成功 `payOrder` → `pushNewOrderNotice` 推 `{type:1, orderId, content}` 给所有在线后台连接。
- **前端**：`utils/orderNotify.js` 登录后自动连 + notification 弹窗 + SpeechSynthesis 语音；收到推送后 `orderNotifyTick` 自增，工作台监听它自动刷新统计卡/营业额。
- **单实例内存版**：真多实例要补 Redis pub/sub 跨实例广播（task_plan 阶段 11）。
- **连接可靠性加固（2026-10-22）**：前端加 25s 心跳 ping（后端回 pong）、握手 10s 超时、无限退避重连（3s→30s，登出/token 清除时停止）、页面 `visibilitychange`/`online` 时断线立即重连；重连成功后触发一次 `orderNotifyTick` 作为对账。后端 `broadcast` 串行发送（`WebSocketSession` 非线程安全），发送失败或半开连接直接从注册表移除，避免僵尸 session 导致后续推送持续丢失。
- **语音播报的浏览器限制（2026-10-22）**：`speechSynthesis` 受自动播放策略限制，**页面刷新后如果没有用户交互，语音会被静默拒绝**（表现：弹窗正常、没有声音；退出登录点击过页面后才恢复）。处理：首次 `pointerdown`/`keydown` 时播一条音量为 0 的短语音预热解锁；缓存中文语音（`voiceschanged` 后再取）；播报前 `resume()`，`cancel()` 后延迟 60ms 再 `speak()`（Chrome 偶发吞掉）。
  - **使用注意：刷新页面后，随便点一下后台页面任意位置（菜单、空白处都行），之后来单就会正常播报。** 如果刷新后完全不点击页面，浏览器仍会拒绝发声，代码层面无法绕过。

## 遗留风险（已决策暂不修，勿反复提起）

| 风险 | 影响 | 当前处置 |
|---|---|---|
| `JwtInterceptor.afterCompletion` 在鉴权失败（preHandle 抛异常）时不执行，`UserContext` 的 ThreadLocal 未清除 | Tomcat 线程复用，理论上可能读到上一个请求的 userId | 用户已决定暂不修 |
| `wx_user` / `user` 表 `openid` 排序规则大小写不敏感 | 理论上有登录串号风险 | 用户已决定暂不修 |
| 数据库凭据曾进入 git 历史，MySQL 侧明文密码未处理 | 凭据泄露面 | 配置已移出版本库，历史与库侧未清理 |
| `CorsConfig` 放开所有来源 + `allowCredentials(true)`（common 与 gateway 各一份，2026-10-18 补 gateway 版） | 仅开发期可接受 | 注释已标注生产需收紧 |

## 资源

### 三个相关项目地址

| 项目 | 本地路径 | 说明 |
|---|---|---|
| 电商小程序（C 端 uniapp） | `D:\study\my-workspace\wechat-workspace\e-commerce-uniapp` | 顾客端小程序，对应 `user` 模块 / `/api/**` |
| 电商后台管理系统前端 | `D:\study\my-workspace\wechat-workspace\e-commerce` | Vue3 + ant-design-vue，对应 `admin` 模块 |
| 电商后台管理系统后端（待重构） | `D:\study\my-workspace\wechat-workspace\e-commerce\server` | Node.js（Express + SQLite），准备整合进本项目做微服务拆分 |

- 架构与约定入口：**本文档「项目架构约定」一节**（2026-09-28 由原根目录 `project_spec.md` 全文并入，含三端划分、命名规则、OpenAPI 同步约定、头像上传待办表）
- 接口文档：网关聚合 `/swagger-ui.html`、各服务 `/v3/api-docs`（`SPRINGDOC_ENABLED=true` 时开；手写 `docs/openapi.yaml` 已删）
- 配置：`src/main/resources/application.yml`；本地真实值走环境变量（清单见 `docs/env.md`）

## 历史结论速查（2026-10-10 ~ 2026-10-18）

> 这段时间的完整流水账已清理，只保留仍然有效的结论。

### 头像 / 静态资源
- 网关补 `/upload/**` → `lb://user-service`（与 `/image/**` → product-service 对称）；静态资源在 `/api/**` 外，天然免登录。
- 头像存相对路径，返回时用 `common/utils/UrlUtil.fullUrl()` 拼绝对地址；`http(s)://` 外链原样返回，避免二次拼接。
- **`X-Forwarded-*` 不可信**：Gateway 的 `XForwardedHeadersFilter` 取的是 `lb://` 已解析后的实例地址，客户端自带值会被覆盖。正解是网关路由加 `PreserveHostHeader`，`UrlUtil` 以 Host 优先、`X-Forwarded-Host` 仅兜底。

### SQL 规范
- 关键字/内置函数统一大写；表名、列名、别名保持小写；`` `user` ``/`` `admin` `` 保留反引号。
- XML 正文里的 `<` 必须写 `&lt;`；MyBatis 多参数方法必须全部标 `@Param`。

### 下单幂等（2026-10-14）
- key `order:idempotency:{userId}:{idempotencyKey}`，value `PENDING`/`orderId`，TTL 24h。
- 流程：读 key（PENDING→409，orderId→返回原单）→ `SETNX` 占位 → 下单 → 成功写 orderId、失败删占位。
- Redis 挂了不阻断下单：`safeGet`/`safeSetIfAbsent`/`safeSet`/`safeDelete` 降级为普通下单。
- 前端进入确认页生成一次 key，失败重试复用，重新提交重新生成。
- 下单成功移除购物车由后端同事务删除，前端 `clearCart` 仅兜底。

### 时间与时区
- 业务时间一律 Java `LocalDateTime.now()` 传参，SQL 不用 MySQL `NOW()`（服务器 UTC，比北京时间早 8h）。
- 订单软删：`DELETE /api/orders/{id}` 只置 `is_deleted=1`，不限状态；列表查询带 `is_deleted=0`。

### 售后归属
- `after_sales` 主表没有 `user_id`，C 端隔离靠 `LEFT JOIN orders` 取 `o.user_id`；原 Node 只按 shopId 过滤，存在越权，迁移时已修。
- 迁移丢弃 11 条孤儿 `after_sale_only_refunds`；`after_sale_items` 字段名 `after_sales_id` 已 ALTER 为 `after_sale_id`；保留跳号 id（重映射风险大）。

### 订单/商品搜索与双文案
- 订单搜索按 `order_no`/`consignee`/`product_name`（EXISTS）；商品搜索按 `name`/`sub_title`/`brand`，keyword 优先于 categoryId。
- `OrderStatusEnum` 有 `desc`（顾客）/`adminDesc`（商家）；`PayTypeEnum` 存英文 code，VO 返回中文 `payTypeDesc`；前端下单统一传 `WeChat`，支付成功才写 pay_type。
- 小程序 `OrderCard` 放 `pages/order/` 局部注册，不进全局 `components/`。

### 物流与 admin
- 物流轨迹目前是假数据（`CourierEnum` + 按订单 id 生成）；2026-10-26 起只返回当前时间之前已发生的轨迹（`addTraceIfPast`），不再出现未来时间；真实微信物流助手未接。
- `admin` 表种子 `admin/admin123`，密码明文，生产需换 BCrypt。
- `GET /api/admin/info` 走 admin 分流，避免 admin token 调 `/api/user/info` 403。

### 环境与网关
- MySQL 统一本机 3306（`e_commerce_db` 账号），不再用 Docker 3307；远程 `193.112.176.228:3306/baota_ecommerce` 勿动；本地每晚 22:00 备份，保留 7 天。
- gateway 是 WebFlux，CORS 用 reactive `CorsWebFilter`；与下游 common 的 Servlet `CorsFilter` 会重复加头，需 `dedupeCorsHeaders` 只留第一个。
- 凭据统一走环境变量（`application-dev.yml` 已删，清单见 `docs/env.md`）；微信凭据只在 user-service 按需加载（`@EnableConfigurationProperties`）。
- 前端 `e-commerce` 的 `VITE_API_BASE_URL` 指向网关 8080。
- `user.id_number` 代码已删，DB 空列待手动 DROP（不阻塞）。

## 售后增强 + 商品评价（2026-10-21）

- **单品售后**：`CreateAfterSaleDTO.orderItemId` 定位单个订单项，退款金额 = 单品小计；不传则整单（兼容旧调用）。
- **货物状态**：`after_sales.goods_status`（未收到/已收到/已拒收），已发货订单申请时必填，`status-constants` 下发。
- **两套退款原因**：未发货 5 个 / 已发货 9 个，`AfterSaleReasonEnum.shipped` 区分，前端按订单状态选。
- **仅退款规则**：only_refund 允许状态 2/3/4，不含已完成 5；退货退款/换货要求 4/5。
- **详情物流**：`AfterSaleVO` JOIN orders 带出 courier/courierDesc/trackingNumber，`CourierEnum` 转中文。
- **商品评价**：`product_reviews` 表 + 一单一评、已完成可评；订单 VO 带 `reviewed`；小程序评价页真实提交 + 晒图/视频。
- **退货/换货页面**：退货方式弹窗（上门取件/自行寄回）、回填物流页、换货发货地址 `ship_address`；详情时间轴按状态动态、拒绝态红色叉。
- **售后列表**：订单列表「售后」Tab + `after-sale-card`；「我的」页角标按状态回填真实数量。

## 营业额统计 + 取消订单自动退款（2026-10-22）

### 1. 营业额统计接口（order-service）

- **归属**：新建 `order-service/controller/ReportController`（`/api/admin/report`）+ `ReportService`/`ReportServiceImpl`，与订单 CRUD 解耦；后续订单统计、销量 Top10 可在此扩展，不再往 `AdminOrderController` 里塞。
- **接口**：
  - `GET /api/admin/report/turnover?period=yesterday|week|month` → `TurnoverVO{timeDesc, turnover}`（工作台卡片，一次一个维度）
  - `GET /api/admin/report/turnover-report?period=yesterday|week|month` → `TurnoverReportVO{dateList, turnoverList}`（ECharts 折线，按天）
  - 原 `quarter` 维度已按前端需求移除（2026-10-22），后端传 quarter 返回 400
- **口径（实时统计，不落统计表）**：营业额 = 实收 − 退款。
  - 实收：`orders.pay_amount` 按 `pay_at` 落在区间内合计（`pay_at IS NOT NULL`，**含已取消订单**，因为取消自动退款后实收和退款要同时体现、净额抵消）。
  - 退款：`after_sale_items.refund_amount` JOIN `after_sales`（`status='refunded'`、`is_deleted=0`），**按原订单的 `pay_at` 归属**，不按退款时间；因此历史营业额会随后续退款动态减少。
  - 折线按 `DATE_FORMAT(pay_at, '%Y-%m-%d')` 分组，退款同样按原订单 `pay_at` 分组；缺失日期补 0。
- **period 语义（2026-10-23 重构）**：先解析成日期闭区间再查库：`yesterday`=昨天 `[昨天, 昨天]`；`week`=近 7 天含今天 `[今天-6, 今天]`；`month`=本月至今 `[月初, 今天]`。查询上界统一取 `end.plusDays(1).atStartOfDay()`，与原 `now` 上界等价（`pay_at` 不会晚于当前时刻）。日期均用 JVM 本地时区，与 `pay_at` 同基准。
- **前端对接（2026-10-22）**：后台工作台新增 `TurnoverPanel.vue`（维度切换 + 大数字 + 图表），`api/report.js` 封装两个接口，`useWorkplaceData` 统一取数；原 `MonthlySales.vue`（近 12 月柱状图、前端本地聚合）已删除。近7天/本月用 `LineChart` 折线；昨日用 `BarChart` 做「前日 vs 昨日」双柱对比（复用 `period=week` 报表，按日期取前天/昨天两点，不新增后端接口）。
- **图表坐标规则（2026-10-22）**：营业额图纵轴传 `yAxisInterval=2000`，组件按数据量级选 2000 的整数倍（约 8 档）——2.5 万→4000、5 万→8000、22 万→28000；纵轴刻度加千分位，`grid.containLabel` 防长数字裁切。横轴 `grid.right=24`、`boundaryGap=['2%','2%']`、`hideOverlap=true`，防首尾日期和本月多日期标签裁切/重叠。
- **工作台四张统计卡（2026-10-22）**：新增 `GET /api/admin/report/dashboard` → `DashboardStatsVO{todayTurnover, yesterdayTurnover, todayOrders, yesterdayOrders, pendingShip}`，一次取齐「今日销售额 / 今日订单量（含环比）/ 待发货订单」；营业额复用 `turnoverOf` 口径，订单量按 `created_at` 且排除已取消（status 6），待发货 = status 3。用户总量改由 `/api/admin/users/statistics` 新增的 `totalUsers` 字段提供（`user` 表按 shop_id count，不再由前端累加近 12 月新增）。前端 `cards` 全部改读后端，不再用订单列表本地聚合。
- **网关路由（2026-10-22 已修）**：`gateway/application.yml` 的 order-service 路由原只覆盖 `/api/orders/**` 与 `/api/admin/orders/**`，`/api/admin/report/**` 会落到兜底 `/api/**` → user-service 返回 404。已把 `/api/admin/report,/api/admin/report/**` 加入 order-service 的 `Path` 断言；改完需重启 gateway 才生效。

### 2. 取消订单自动生成退款售后单

- **触发点**：用户端 `OrderServiceImpl.cancelOrder`、后台 `AdminOrderServiceImpl.cancelOrder` 各加同一段逻辑；只对**已支付未发货**（状态 2 `PENDING_CONFIRM` / 3 `PENDING_SHIP`）生效，待付款取消不生成。
- **实现**：新增 `order-service/mapper/AfterSaleRefundMapper`，用注解 SQL **直写售后三表**（`after_sales`/`after_sale_items`/`after_sale_only_refunds`），与订单取消同事务；不调 aftersale-service 接口（共享库方案，与订单服务直接读 products 表同思路）。
- **落库形态**：`after_sale_type=only_refund`、`status=refunded`、`reason=cancel`（枚举 `AfterSaleReasonEnum.CANCEL` 已存在）、`goods_status=not_received`、`images='[]'`；明细按订单项快照，`refund_amount=sub_total`（整单退）；`refund_at=refunded_at=取消时间`。
- **防重**：取消前 `countAfterSale(order_id, shop_id)`，已有未删除售后单则跳过（退款已走售后流程，不重复退）。
- **单号**：`AS + yyyyMMddHHmmss`（照 aftersale-service 的 `generateAfterSaleNo`）。`after_sales.after_sale_no` 当前**无唯一索引**，同秒并发取消可能产生重复单号，但不影响主键/业务。
- **与营业额的关系**：自动退款单 `status='refunded'`，会被 `sumRefundAmount` 统计到，并按原订单 `pay_at` 归属，与取消订单的实收在同一区间抵消。

## 营业额报表重构 + 按天 SQL 修正（2026-10-23）

### 1. 结构：period 只算日期，日期列表只写一遍

- `ReportServiceImpl.resolvePeriod(period)` 返回 `PeriodRange(begin, end, timeDesc)` 记录，switch 只负责算起止日期；`getTurnoverStats`（卡片）与 `getTurnoverReport`（折线）共用一份，不再各写一套 switch。
- `getDateList(begin, end)` 用 `while (!d.isAfter(end))` 生成闭区间日期，全类只写一次；用局部变量 `d` 递增，不修改入参 `begin`。
- `turnoverOf(LocalDate begin, LocalDate end)` 内部转 `[begin 0点, end 次日 0点)`；工作台今日/昨日营业额也改用日期入参。

### 2. 口径不变，上界从 now 改为「end 次日 0 点」

- `week`/`month` 原上界是 `now`，重构后是今天次日 0 点；因为 `pay_at` 不会晚于当前时刻，结果等价。
- 唯一差异：库里若有未来时间的 `pay_at`（测试/补偿数据），新写法会计入。

### 3. 按天分组 SQL 的坑（`sumPaidAmountByDay` / `sumRefundAmountByDay`）

- **`GROUP BY pay_at` 是错的**：`pay_at` 精确到秒，按它分组等于每笔订单一组，同一天出现多行；Java 端 `toDateAmountMap` 用 `put` 转换时后一条覆盖前一条，折线每天只剩一笔金额。必须 `GROUP BY DATE_FORMAT(pay_at, '%Y-%m-%d')`。
- **必须带 `is_deleted = 0`**，与单区间 `sumPaidAmount`/`sumRefundAmount` 的条件保持一致（单店模式已无 `shop_id`）。
- 退款按天同样按**原订单** `o.pay_at` 分组（`a.status='refunded'`），与 `sumRefundAmount` 口径一致。
- MyBatis 参数必须全标 `@Param("start")/@Param("end")`，与单区间查询命名一致；只标部分参数时其余依赖编译期 `-parameters`，不可靠。
- XML 正文里的 `<` 必须写 `&lt;`（`>` 可以不转义），否则 mapper 解析直接报 well-formed 错误；SQL 里也注意不要出现重复的 `WHERE`。

## 商品收藏（2026-10-23）

### 1. 归属与表

- 放 **product-service**：收藏列表要 join `products` 取名称/图/价格，同库直接 join，不用跨服务 Feign（与订单服务直接读 products 同思路）。
- 表 `product_favorites(id, shop_id, user_id, product_id, created_at)`：
  - `uk_shop_user_product (shop_id, user_id, product_id)` 保证一个用户在同一店铺对同一商品只有一条；
  - `idx_shop_user_time (shop_id, user_id, created_at)` 覆盖列表查询；
  - `utf8mb4_general_ci`，与服务器 MySQL 5.7 兼容。
- 取消收藏**物理删除**，不做 `is_deleted`：收藏不是业务单据，不需要留痕；重新收藏也不会产生重复行。
- `created_at` 用 Java `LocalDateTime.now()` 传参，不用 MySQL `NOW()`（时区差 8 小时）。

### 2. 接口与鉴权

- `GET /api/favorites`（按收藏时间倒序）、`GET /api/favorites/check?productId=`、`POST /api/favorites/{productId}`、`DELETE /api/favorites/{productId}`。
- 不在 `EXCLUDE_AUTH_PATHS` 白名单 → JwtInterceptor 要求 user 令牌；`userId`/`shopId` 从 `UserContext` 取，不信任前端参数。
- `shopId` 缺失时回退模板店 1，与 `ProductServiceImpl` 商品浏览口径一致（多租户沙箱接入时一起切换）。
- 幂等：收藏用 `INSERT ... ON DUPLICATE KEY UPDATE id = id`；取消不存在的记录删 0 行也按成功返回。
- 收藏前用 `productMapper.findById(productId, shopId)` 校验商品存在且属于本店；该查询**不筛 `listing_status`**，所以下架商品也能收藏。

### 3. 列表口径

- 列表 join `products` **不按 `listing_status` 过滤**：下架商品继续显示，`status` 下发给前端打「已下架」标记；重新上架后仍是已收藏状态。
- `products` 表没有 `is_deleted`（删除是物理删），被删商品 join 后自然查不到，不会出现空行。

### 4. 网关路由

- product-service 路由补 `/api/favorites,/api/favorites/**`；不补会落到兜底 `/api/**` → user-service 404（与 `/api/admin/report` 同类坑）。改 gateway 配置需重启。

### 5. 小程序端

- `pages/goods/detail.vue` 原先把收藏 id 存 `uni.setStorageSync('favoriteIds')`（假收藏，换手机/清缓存就丢），改为后端接口：进页已登录时 `checkFavorite` 回显；点击先 `ensureLogin()` 静默登录，再按当前状态 POST/DELETE，`favoriteLoading` 防连点，成功后才翻转星标。
- 新增 `pages/my/favorites.vue`：`ensureLogin` 后拉列表；卡片展示图/名称/售价/划线价，右上角星标取消收藏（成功后本地移除）；空态「去逛逛」；下架商品打标但仍可点进详情。
- 「我的」页入口「收藏与浏览」保留原名，本期只做收藏（浏览记录后续可同页加 Tab）。

## 换货子表修复（2026-10-23）

### 1. namespace 被换行截断 → BindingException → 500

- `AfterSaleExchangeReturnMapper.xml` 的 `<mapper namespace="ecommerce.aftersale.mapper.AfterSaleExch\nangeReturnMapper">` 中间断行，XML 属性值里的换行会被规范化成空格，实际 namespace 变成 `...AfterSaleExch angeReturnMapper`，与接口 FQN 对不上。
- MyBatis 启动不报错，但 XML 里的语句不会绑定到接口；调用时抛 `Invalid bound statement (not found)`，被 `GlobalExceptionHandler` 兜底成 500「服务器内部错误」。
- 影响该子表的全部操作：换货详情（`findByAfterSaleId`）、创建换货售后（`insertPlaceholder`）、回填退货方式/物流、后台审核时间戳、软删除——换货链路此前整体不可用。
- 修复：namespace 写回完整一行。排查同名问题时，可用脚本扫描所有 mapper XML 的 namespace 是否含换行/空格。

### 2. 创建换货售后未保存退货方式

- 前端 `exchange.vue` 提交时带了 `returnWay`/`returnTimeSlot`，`CreateAfterSaleDTO` 也接收，但 `insertSubPlaceholder` 的 `EXCHANGE` 分支只写了 `shipAddress`，`insertPlaceholder` SQL 也没有这两列。
- 结果：换货详情一直显示「退货方式：—」；回填物流时 `detail.returnWay` 为空，后端 `submitReturn` 校验 `return_way 必须是 pickup 或 self`，提交被拒。
- 修复：`AfterSaleServiceImpl` 的 `EXCHANGE` 分支补 `.returnWay(returnWay).returnTimeSlot(returnTimeSlot)`；`AfterSaleExchangeReturnMapper.xml` 的 `insertPlaceholder` 增加 `return_way`、`return_time_slot`。
- 修复前创建的换货单子表这两列为 NULL，历史数据无法恢复用户选择，需要重新申请或手工补值。

## 全局 LocalDateTime 格式化（2026-10-23）

- 现象：后台售后模块时间显示 `2026-10-21T10:44:55`。根因是 `AfterSaleVO` 除 `payAt` 外的时间字段没有 `@JsonFormat`，Jackson 默认把 `LocalDateTime` 序列化成 ISO-8601（日期和时间之间带 `T`）。
- **`spring.jackson.date-format` 对 `LocalDateTime` 无效**：该属性只设置 `java.util.Date`/`Calendar` 的 `DateFormat`；`spring.jackson.time-zone` 对不带时区的 `LocalDateTime` 也无意义；`write-dates-as-timestamps: false` 只是不输出数组/时间戳，不会去掉 `T`。所以只加 yml 解决不了。
- 正确做法（Boot 4.1 / Jackson 3）：在 `common` 注册 `JsonMapperBuilderCustomizer`，用 `tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer` + `SimpleModule` 覆盖 `LocalDateTime` 序列化：
  - `common/src/main/java/ecommerce/configuration/JacksonConfig.java`
  - 注意不是网上常见的 `Jackson2ObjectMapperBuilderCustomizer`，Boot 4 已改用 Jackson 3 的 `JsonMapperBuilderCustomizer`。
- 只注册序列化器，不动反序列化：Jackson 默认仍能接收 ISO（含 `T`）格式，避免影响请求体解析。
- 字段级 `@JsonFormat` 优先级更高（`OrderVO` 各时间、`AfterSaleVO.payAt` 输出不变）。
- common 被所有业务服务依赖（`scanBasePackages = "ecommerce"`），配置自动生效；gateway 不引 common，但它是 WebFlux 网关、无业务 VO，不受影响。
- 验证：`mvn -pl common,aftersale-service -am compile` 通过，并用 Jackson 3 实测 `LocalDateTime.of(2026,10,21,10,44,55)` → `"2026-10-21 10:44:55"`。

## 意见反馈（2026-10-23）

- 表 `feedback`（手工建表，DDL 未纳入 `docs/sql`）：`id / shop_id / user_id / type / content / images / contact / status / is_deleted / created_at / updated_at`。
  - `type` 存 code：`suggestion` 功能建议 / `product` 商品问题 / `order` 订单物流 / `other` 其他；
  - `images` 存 JSON 数组字符串（与 `product_reviews.images` 同款），只存 `/image/feedback/xxx` 相对路径；
  - `status` 预留后台处理状态（0 未处理 / 1 已处理）；
  - `created_at` 不用 MySQL 默认值，由 Java `LocalDateTime.now()` 写入（服务器时区 UTC 差 8 小时）。
- 接口：`POST /api/feedback`（user-service，需 user 令牌；`/api/feedback` 不在免登录白名单，网关兜底 `/api/**` → user-service，无需新增路由）。
  - 校验：类型白名单；内容 trim 后 5~1000 字；截图最多 6 张且必须以 `/image/feedback/` 开头（防外链）；联系方式 ≤64 字；
  - `userId` 取 `UserContext`，`shopId` 缺失回退模板店 1；落库用 MyBatis-Plus `BaseMapper.insert`，返回反馈 ID。
- 图片上传：`UploadServiceImpl.TYPE_SUBDIR` 新增 `feedback → upload/image/feedback/`，前端用 `uploadMedia(path, 'feedback')`。
- 前端 `pages/my/feedback.vue`：类型胶囊单选（code + label）、内容 200 字计数、最多 6 张截图上传/预览/删除、联系方式默认带出账号手机号；提交调真实接口，成功返回上一页。


## 工作台全局时间选择器 + 销量排名（2026-10-24）

### 1. 全局时间选择器（前端）
- 选择器从 `TurnoverPanel` 提到 `Workplace.vue` 报表区顶部，维度仍是 `yesterday / week / month`；`useWorkplaceData` 中 `turnoverPeriod` 改为 `reportPeriod`，由 `setReportPeriod(period)` 统一触发营业额、销量排名、用户增长刷新。
- 订单统计没有接口，改为前端按时间段聚合：
  - 昨日：`created_at` 按小时分 24 桶（`HH`）；
  - 近7天/本月：按天分桶（`YYYY-MM-DD`），本月从月初到今天；
  - 三条线仍是待付款(1)/已付款(2-5)/取消(6)，环比对比上一等长区间（本月对比上月同期，按已过天数对齐）。
- 营业额图保留原设计：昨日仍是「前日 vs 昨日」双柱，其余按天折线；其余三张图昨日按小时展示，粒度与营业额不同是有意为之。
- 用户增长「用户总量」曲线改为「期初总量 + 期间新增累计」，期初总量 = 后端 `totalUsers` − 本时间段新增，避免短周期累计线从 0 开始。

### 2. 用户统计接口支持 period（user-service）
- `GET /api/admin/users/statistics?period=yesterday|week|month`，默认 `year` 保留原近 12 月行为；`UserStatisticsVO` 的 `months` 改为 `buckets`，新增 `bucketType`（hour/day/month）。
- `UserMapper.countMonthlyNewUsers` 改为 `countNewUsersByBucket(shopId, start, end, format)`：`format` 传 `'%H' / '%Y-%m-%d' / '%Y-%m'`，用 `DATE_FORMAT(create_time, #{format})` 分组；起止时间由 Java `LocalDateTime` 传入，避开 MySQL 服务器 UTC 时区。
- 前端用户统计调用改为 `getUserStatistics(period)`，`api/user.js` 透传参数。

### 3. 销量排名（order-service）
- `GET /api/admin/report/sales-ranking?period=&limit=8` → `List<SalesRankingItemVO>{productId, productName, image, salesQuantity}`。
- 口径：`orders.pay_at` 落在周期内，排除待付款(1)/已取消(6)，退款暂不扣减；按 `order_items.quantity` 汇总，按件数降序；商品名/图取订单项快照，同一商品多条快照用 `MAX()` 取一条。
- `ReportServiceImpl` 复用 `resolvePeriod`，limit 限制在 1~20。
- 前端 `OrderHistory.vue`（最近订单时间轴）删除，换 `SalesRanking.vue`；`useWorkplaceData` 移除 `recentOrders`、订单状态字典请求、`orderStatusClass` 等无用逻辑。

## AI 导购流式对话 + Redis 会话（2026-10-24，2026-10-26 迁移到 Python）

> 2026-10-26 起 AI 能力由 `recommender-python`（FastAPI + LangGraph）提供，Java `ai-agent-service` 与 Spring AI 已删除；下面保留前端契约与历史约定，后端实现以 Python 仓库为准。

### 1. 后端契约（Python ai-service；路径与前端约定不变）
- `POST /api/agent/chat/stream`（SSE）与 `POST /api/agent/chat`（非流式）均接收 `{ message, sessionId, scene }`。
- 第一阶段统一走导购 prompt（多轮对话 + 商品问答），`scene` 接收但暂不区分场景。
- 流式每块输出纯文本 `data: <增量>`，LF 行尾、无 `[DONE]`/JSON/心跳；换行拆成多条 `data:` 行。
- 商品数据由 Python 直连 MySQL 只读（`products` / `categories`），上架商品内存缓存 60 秒；检索不到时如实说明，不编造。

### 2. Redis 会话历史
- key `agent:chat:history:{sessionId}`，list 存 `user:xxx` / `assistant:xxx`，保留最近 12 条（约 6 轮），TTL 2h（Python 沿用同一 key 与格式，切换时旧会话可继续用）。
- Python 把历史转成 OpenAI messages 列表，和本轮消息一起交给模型；Redis 读写全部 try/catch，挂了自动降级为单轮。
- Redis 超时设 500ms（`protocol=2`，兼容本地老版本 Redis），不拖慢首字。

### 3. 小程序端
- `api/agent.js`：`wx.request({ enableChunked: true })` + `onChunkReceived` 收 SSE 分片；基础库 < 2.20 或非 MP-WEIXIN 自动降级为非流式 `/chat`。
- SSE 解析按字节找 `\n\n` 边界后整段 UTF-8 解码，避免中文多字节被分片截断；无 `data:` 前缀的续行按内容处理。自实现 `utf8Decode`，不依赖 TextDecoder。
- 客服页 `pages/my/customer-service.vue`：**AI 流式优先**；「人工客服」本地拦截（要拉起原生客服会话）；AI 失败/超时/空回复时用本地规则兜底，没有规则再走人工客服 + 意见反馈；「正在思考…」→ 首字到达即替换；AI 回复标「AI 生成」。
- Vue 3 注意：push 进 reactive 数组后，直接改原始对象不触发更新，`append()` 返回 `this.messages[len-1]` 的响应式引用。
- `sessionId` 每次进入页面重新生成（`cs-时间戳-随机`），同一页面内多轮共享；离开页面再进是新会话。

### 4. 待办
- AI 下单/加购（写操作，需用户二次确认）。
- RAG 知识库、商品卡片渲染（前端目前只渲染纯文本，需前后端一起改）。
- `SILICONFLOW_API_KEY` 配在 `recommender-python/.env`（本地）或部署 `.env`（生产），缺失时 AI 请求失败会走前端本地兜底。

## 2026-10-26 生产部署与线上问题

### AI 迁移上线
- Python `ai-service` 容器上线（FastAPI + LangGraph）；网关 `/api/agent/**` 静态转发，Docker 里由 compose 的 `AI_SERVICE_URL` 覆盖为 `http://ai-service:8000`，本地默认 `127.0.0.1:8000`。
- 对话接口强制 JWT（与 Java 共用 `JWT_SECRET`）+ 每用户限流 + 全局并发 + 首字/总时长超时 + `max_tokens` 上限。
- 线上验证：`/api/home` 200、`/api/cart/count` 401（需登录）、`/api/agent/chat` 401（网关已到 Python，鉴权生效）。

### 公网延迟定位（分类页慢）
- 服务器本机：`/api/categories` 10ms、`/api/home` 46ms、`/api/products?categoryId` 15ms。
- 公网：0.8～2s；Redis INFO 命中率约 85%、事件循环 <1ms，排除 Redis。
- 瓶颈是 Cloudflare 跨境代理；分类页串行两个请求（先分类、再商品），体感更慢。待决定是否灰云直连。
- 分类数据可做小程序本地缓存（10～30 分钟）或 CF Cache Rule，降低重复访问延迟。

### 分类图 webp 与 Docker bind mount
- 生产库 `categories.image` 已是 `.webp`；文件上传到 `/www/java/upload/image/category/` 后，容器内仍是旧 png。
- 原因：整个 `upload` 目录被替换，容器 bind mount 还钉在旧 inode。
- 处理：`docker compose up -d --force-recreate product-service user-service` + 清 Cloudflare `/image/category/*`。
- 结论：以后替换整个挂载目录后，凡挂载它的服务都要重建。

### 物流轨迹未来时间
- `buildLogistics` 按发货时间 + 2/6/18/22/24 小时（再叠加 id 偏移）生成假轨迹，未判断是否已发生，今天发货会显示明天轨迹。
- 修复：`addTraceIfPast`，只保留时间不晚于 `LocalDateTime.now()` 的轨迹；后台和小程序共用同一接口，一起生效。

### 部署注意（2026-10-26 手工 jar 流程，已被 2026-10-28 TCR 流程替代）
- 生产用 `deploy/docker-compose.yml`（8 个 Java 服务 + `ai-service` + Redis/Nacos），不要传根目录的开发 compose（只有 Nacos + MySQL）。
- 旧流程：jar 打进镜像，上传 jar 后必须 `docker compose build <service>` 再 `up -d`，只 `up -d` 仍用旧镜像。
- 2026-10-28 起改走 TCR 镜像，服务器用 `--no-build` 拉取，详见下节。

## 2026-10-28 发布切 TCR 镜像

### 结论
后端发布从「本地 `mvn package` → scp jar → 服务器 `docker compose build`」改为「本地构建镜像 → 推腾讯云 TCR → 服务器 `pull + up --no-build`」。线上 9 个业务容器已全部换成 TCR 镜像，网关 `:8080` 和公网 `https://api.jiemids.com/api/products/1` 均返回 200。

### 固定信息
- TCR：`ccr.ccs.tencentyun.com`，命名空间 `jiemi-ecommerce`，9 个仓库：8 个 Java 服务 + `ai-service`
- 服务器：`/www/java`；SSH 别名 `ecommerce`（root@193.112.176.228，私钥 `~/.ssh/ecommerce.pem`；`~/.ssh/ecommerce_deploy` 作备用）
- compose：每个服务同时有 `image: "${REGISTRY:-local}/<svc>:${TAG:-latest}"` 和 `build:`；服务器部署必须 `--no-build`
- `.env`：本地 `deploy/.env` 与服务器 `/www/java/.env` 都有 `REGISTRY=ccr.ccs.tencentyun.com/jiemi-ecommerce`
- 首次线上 tag：`20261028-3af36f5-dirty`（工作区有未提交改动，脚本自动加 `-dirty`）
- 生产库已确认有 `shops.visitor_key`、`orders.is_subscribe_unpaid`、`orders.unpaid_notify_sent`、`idx_orders_unpaid_notify`

### release.sh
- 位置 `deploy/release.sh`；白名单 8 Java + `ai-service`，`common` 不能部署
- 流程：Maven（只 Java）→ `docker build`（Java context=`<svc>/target`；ai context=`../recommender-python`）→ `docker push` → SSH `docker pull <目标镜像>` → `docker compose -f docker-compose.yml up -d --no-build --no-deps <svc>` → `docker inspect` 状态 → 重新生成 `docker-compose.override.yml`
- 常用：
  - `./deploy/release.sh all`
  - `./deploy/release.sh shop-service`
  - `--no-deploy` 只推送；`--no-build` 跳过 Maven；`--no-ai` 排除 Python；`--dry-run` 只打印
  - `--rollback <tag> all` 跳过构建/推送，直接部署指定 tag
- tag 默认 `日期-git短SHA`，工作区脏加 `-dirty`
- 部署前预检：SSH 免密 + 服务器 compose 含 `REGISTRY`，不满足直接退出

### release-web.sh（后台管理端前端）
后台前端是静态站点，不走 TCR，单独用 `deploy/release-web.sh` 发布，与后端 `release.sh` 放在同一目录。
- 流程：`../e-commerce` 执行 `npm run build` → `tar` 上传到服务器 `/tmp/admin-dist` → `rsync -a --delete` 到 `/www/wwwroot/jiemids.com`
- 站点目录保留 `.env`、`.htaccess`、`.user.ini`、`.well-known`、`public`，只替换 `index.html` / `assets` / `image` / `region`
- 发布前 `rsync --link-dest` 硬链接快照到 `jiemids.com.bak.<时间戳>`，默认保留 3 份；`.user.ini` 被宝塔 `chattr +i`，不能硬链接，需排除
- 常用：`./deploy/release-web.sh`、`--no-build`、`--dry-run`、`--rollback`
- 静态文件不需要 reload Nginx；`assets` 带 hash，若 `index.html` 被 Cloudflare 缓存需手动清一次

### 多服务 tag 相互牵连（2026-10-28 修复）
- 现象：单服务发布生成新 tag 后，执行全量 `docker compose up -d` 报 `No such image ... ai-service:<新tag>`。
- 原因：compose 9 个服务共用 `${TAG}`，只有被发布的服务有新 tag，其它服务没发过。
- 修复：
  1. `docker-compose.override.yml` 由 `release.sh` 从运行中的容器自动生成，把每个服务固定到自己的镜像 tag；全量启动不带 `TAG`，直接 `docker compose up -d`。
  2. 部署命令改为 `docker pull` 目标镜像 + `up --no-deps`：`gateway` 的 `depends_on` 包含所有服务，不带 `--no-deps` 会把依赖也按新 tag 去拉。
  3. `TAG` 只内联传给单服务部署，不再常驻服务器 `.env`；override 每次发布后重新生成。

### nacos / redis / upload
- `nacos`、`redis` 用官方镜像，不需要 TCR 仓库；服务器已缓存
- `upload` 是宿主机目录 `/www/java/upload`，bind mount 到 product/user，不是镜像，需单独备份
- 可选：把 nacos/redis `docker tag` 到 TCR，避免 Docker Hub 不稳定

### 踩坑
- `scp/ssh ecommerce` 报 `Could not resolve hostname`：本地 `~/.ssh/config` 没有别名；加 `Host ecommerce` 或直接用 `root@IP`
- root 密码 SSH 失败：改用密钥，宝塔终端把公钥写进 `/root/.ssh/authorized_keys`（目录 700、文件 600）
- 服务器 `docker pull` 401：`docker login` 是按 Linux 用户保存的，执行 compose 的 SSH 用户必须登录过 TCR
- Windows 上传 compose 带 CRLF，和服务器 LF 文件 diff 会整文件变化：用 `diff --strip-trailing-cr` 或 `sed -i 's/\r$//'`
- 服务器 compose 已备份：`/www/java/docker-compose.yml.bak.<时间戳>`
- 后台管理端 `dist` 不走 TCR，用 `deploy/release-web.sh` 单独发布；沙箱复用的 `X-Sandbox-Visitor-Key` 和订单金额标签需要重新 build 上传后才生效

## 运营数据报表导出（2026-10-26）

### 1. 工作台顶部累计指标
- `GET /api/admin/report/headline` → `{totalPaid, totalRefund, netAmount, totalOrders}`，全时段聚合，不再由前端累加前 1000 条订单。
- 累计销售额 = `pay_at` 非空的 `pay_amount` 合计 − 退款（按原订单 pay_at 归属）。
- 累计订单量 = 全时段、未删除、排除已取消（`order_status <> 6`）。

### 2. 本月运营报表
- `GET /api/admin/report/export` → xlsx 流，读取 `template/运营报表模板.xlsx` 填数，单 sheet。
- 统计窗口：当月 1 日至今天（含），2026-10-27 由「近 30 天」改为「本月」。
- 两套订单数口径，不能混用：
  - **有效支付订单**（展示、客单价分母）：当日 `pay_at`、未取消、累计退款 `< pay_amount`。
  - **转化率分子**（不展示）：当日 `created_at` 且 `pay_at IS NOT NULL`，同批口径，后续退款不扣。
- 退款按原订单 `pay_at` 归属；新增用户按 `user.create_time`。
- 概览由本月明细累加；平均客单价 = 总销售额 ÷ 有效支付订单总数。
- 前端工作台右上角「数据导出」按钮，axios `responseType: blob` + `<a download>`；顶部标签由「本年度累计销售额」改为「累计销售额」（后端是全时段口径）。
- 报表底部带口径说明；模板预置 31 行明细样式（覆盖 31 天的月份），本月未过完时导出自动隐藏剩余空行。

### 3. 验证
- `order-service` 编译通过；模板结构校验（38 行、4 个合并区域、31 行明细样式齐全）。
- 5 条按天聚合 SQL 在本地 MySQL 跑通；样本文件生成在 `order-service/target/operation-report-sample.xlsx`。
- 后台前端 `npm run build` 通过（`dist` 已重新生成）。
- **待部署**：新版 order-service jar + 新 `dist`，上线后在工作台点「数据导出」做真实验证。

## 全局悬浮 AI 客服（2026-10-24）

### 1. 结构
- `components/floating-chat/floating-chat.vue`：右侧中间悬浮球 + 底部聊天面板；头像 `/static/zongjie.jpg`（2026-10-25 由本地 webp 转 jpg，修 iOS 不显示）；面板内复用消息气泡、AI 标识、人工客服/意见反馈按钮。
- `store/ai-chat.js`：Pinia 全局会话（messages/sessionId/sending/visible），悬浮球和「专属客服」页共用，切页面不丢消息；`reset()` 开新会话。
- `main.js` 全局注册 `FloatingChat`；`<FloatingChat />` 注入 pages.json 注册的 29 个页面（`pages/my/customer-service` 除外，那里是全屏客服页）。

### 2. uni-app 全局悬浮的落地方式
- uni-app 小程序没有 App 级全局组件；App.vue 不渲染模板。做法是全局注册组件 + 在每个页面模板 `<template>` 后插入 `<FloatingChat />`。
- 注入脚本按 `pages.json` 的 `"path"` 列表处理，跳过 `pages/my/customer-service`；**以后新增页面要手动加 `<FloatingChat />`**，否则新页面没有悬浮球。
- 组件根节点是页面根节点的兄弟（Vue 3 多根节点），固定定位不受页面根容器 `overflow` 影响。
- 悬浮球 `position: fixed`，默认右侧中间（`z-index: 9997`，面板遮罩 9998）；支持 touchstart/move/end 拖动，松手吸附到左右边缘，位置存 Pinia `ballLeft/ballTop`，切页面保持；轻点（位移 ≤4px）打开面板。

### 3. 交互细节
- 点击遮罩关闭；面板内 `@click.stop` 防误关。
- 键盘弹起时用 `uni.onKeyboardHeightChange` 获取高度，遮罩加 padding-bottom、面板缩短，避免输入框被挡。
- 消息区用 `scroll-top` 递增持续贴底；流式 chunk 更新时 deep watch 触发。
- 「新对话」换 sessionId 并清空消息；sessionId 复用后端 Redis 多轮历史。

### 4. 注意
- 悬浮球在登录页/404 也会出现；如果某些页面不需要，在该页面模板删掉 `<FloatingChat />` 即可。
- 悬浮球固定在右侧中间，可能和部分页面右侧的固定按钮重叠，按需调整 `right/top`。
- 专属客服页跳过注入，但共用同一个 Pinia store，所以两处对话是连续的。

## 接口防抖/防重（2026-10-24）

- **搜索**：`pages/search/search.vue` 和订单列表搜索都是 `@confirm` / 按钮触发，不是输入即搜，不需要 debounce（加了反而多等）。
- **滚动**：全项目没有 `@scroll`/`scrolltolower`/`onReachBottom` 触发请求，不需要 throttle。
- **分类切换**：`category.vue` 的 `loadProducts` 加 `loadSeq` 请求序号，只应用最后一次点击的响应，避免快速切分类时旧响应覆盖新分类。
- **提交防重**：订单列表/待付款页的付款、取消、确认收货、删除、再来一单加 `operating` 标志；仅退款/退货退款/换货/回填物流加 `submitting` 标志；成功跳走前不重置，失败才允许重试。
- 下单确认页原本已有 `paying` 标志，覆盖下单 + 支付，无需重复加。
- 列表商品图统一加小程序原生 `lazy-load`（22 个文件、28 处）；轮播/头像/飞入动画不加。

## 地址/购物车归属校验补齐（2026-10-24）

- 用户决定不再走多租户沙箱方案，直接修掉两个遗留越权点。
- **地址**：`AddressMapper.findById/update/deleteById` 全部加 `user_id` 条件；`AddressServiceImpl.update/remove` 传当前 `UserContext.getUserId()`。原来只按 `id + shop_id` 定位，任何登录用户可改/删他人地址。
- **购物车**：`CartMapper.update` 的 WHERE 从 `id + shop_id` 改为 `id + user_id + shop_id`；`CartServiceImpl` 两处 `update` 的实体都来自 `findByUserAndProduct`，本身带 userId，调用方式不变。
- `order-service` 编译通过。

## 小程序分包 + 图片后端化（2026-10-24）

- **`static/image` 15M 全部删除**：商品图回退改为后端 `/image/upload/{id}.webp`；前端 `static` 从 15M 降到 181K。
- **轮播/分享图移到后端**：`upload/image/swiper/1~4.webp`、`upload/image/share/share-3c.jpg`；上线时必须把整个 `e-commerce-boot/upload/` 上传到服务器 `/www/java/upload/`，否则图片 404。
- **pages.json 分包**：
  - 主包 12 页：4 个 tabBar 页（首页/分类/购物车/我的）+ 我的子页（personal-info/address/address-edit/favorites/about-us/feedback/logistics/customer-service）。
  - 分包 4 个：`pages/goods`（detail）、`pages/search`（search）、`pages/order`（8 页）、`pages/after-sales`（8 页）。
  - `root` 用现有物理目录，`path` 相对 root；完整路由不变（如 `/pages/order/list`），`navigateTo` 无需改。
- **主包不能依赖分包组件**：`OrderCard` 从 `pages/order/` 移到 `components/order-card/`，`AfterSaleCard` 从 `pages/after-sales/` 移到 `components/after-sale-card/`；`pages/my/logistics`、`pages/order/list`、`pages/order/pending-pay` 的 import 已更新。
- 如果构建后主包仍超限，下一步可把「我的」子页物理移到独立分包目录（需改路由），或检查 uview-plus/uni-ui 体积。

## 生产部署落地（2026-10-24 ~ 10-25）

### 1. 服务器与编排
- 腾讯云 8G；Docker Compose；Nacos **3.1.1**（不是 2.3.2）+ **Redis 容器** + 9 个 Spring Boot 服务；MySQL 在宝塔宿主机，容器用 `host.docker.internal` 访问；Redis 在 `my-net` 内网（不映射 ports，密码 `SPRING_DATA_REDIS_PASSWORD`）；gateway 只绑 `127.0.0.1:8080`；Nacos 控制台绑 `127.0.0.1:18080`，不对公网。
- 部署文件：`deploy/`（compose/Dockerfile/.env.example/nginx-api.conf/README）+ 根目录 `nginx`（`www.jiemids.com` 后台站点配置）。
- `.env` 关键：`NACOS_ADDR=nacos:8848`、`DB_*`、`JWT_SECRET`、`WECHAT_*`、`SILICONFLOW_API_KEY`、`REDIS_HOST=redis`、`SPRING_DATA_REDIS_PASSWORD`；`SPRING_PROFILES_ACTIVE=prod`；JDK 17 + `Asia/Shanghai`。
- 图片：把本地 `e-commerce-boot/upload/` 整目录传到 `/www/java/upload/`；product/user 都挂 `./upload:/app/upload`。

### 2. 域名与 Nginx
- 小程序 API：`api.jiemids.com` → Cloudflare → Nginx → gateway 8080；`/api`、`/image`、`/upload`；AI SSE 关 `proxy_buffering`。
- 后台前端：`www.jiemids.com` → Cloudflare → Nginx 静态 `dist`；`/api`、`/image`、`/upload`、`/ws` 全部反代到 8080；`client_max_body_size 20m`；SPA `try_files`；`.env`/`.git` 等返回 404。
- 后台 WebSocket：`wss://www.jiemids.com/ws/order`，Nginx `/ws/` 必须带 `Upgrade`/`Connection "upgrade"`；已实测 101，来单提醒连接成功。

### 3. 踩坑
- 后台登录页「登录」按钮是 `v-if="isDev"`，生产只显示「一键体验」；模板店不能给所有访客 admin 直登，否则环境互相污染，生产必须走沙箱。
- 沙箱 500 一开始是 Nginx `/api` 还指向旧 Node 3000，旧 Node 的 `shopController.js` 正好返回「体验店铺开通失败」；切到 8080 后走 Spring shop-service，沙箱 token（shopId 12/admin_s12）签发成功。
- 微信 `wxlogin` 400：微信 IP 白名单没放行；服务器出口 IP `193.112.176.228` 要加；本地调试的公网 IP 会变，不要长期写死。
- Cloudflare：自签名 → 边缘证书；Full strict 报 526（源站证书没装好）→ 先切 Full；WebSockets 要开；Bot Fight/Under Attack 要关；`cf-ray AMS` 说明绕欧洲，国内慢，正式可改 DNS-only + Let's Encrypt。
- Nacos 8848/9848 不需要公网；控制台 18080 绑 `127.0.0.1`，用 SSH 隧道。
- 小程序：`static/image` 15M 删除 + 分包后主包 ~1.5M；`lazyCodeLoading: requiredComponents` 要放在 `mp-weixin` 根节点（放 `optimization` 下不会生成）。
- 后台前端 `src/utils/file.js` 的 `fs/path` 是未使用的 Node 专用代码，已删；`align-item` 拼写已修正。
- 网站根目录不能放 `.env`；删掉，Nginx 也要 deny。

### 4. 待办
- 微信审核：模拟支付、隐私政策/用户协议真实页面、AI/UGC 内容安全（VIP/优惠券/积分/协议死链已于 2026-10-28 隐藏）。
- 生产安全：安全组关闭 8848/9848/9849/18080/9001~9008；停旧 Node 3000；删网站根 `.env`。
- 沙箱：`templateGuard`（过期回收 2026-10-27 已完成）。
- Cloudflare 跨境延迟（HKG）；正式考虑 DNS-only + Let's Encrypt。

## 线上问题修复（2026-10-25）

### 1. 小程序审核白屏 / 商品不渲染
- 根因：提审上传的是 `unpackage/dist/dev/mp-weixin`，`BASE_URL` 是 `http://127.0.0.1:8080`，审核员手机上所有请求和图片都指向本机。
- 正确包：`unpackage/dist/build/mp-weixin`（`https://api.jiemids.com`）；微信开发者工具必须打开这个目录再上传。
- 已写入 `deploy/README.md` 和 `task_plan.md`，避免复发。

### 2. 分类接口 500 / Redis
- 生产一开始没装 Redis：`/api/categories` 每次卡约 10 秒后 500；商品接口不受影响。
- 代码侧：`ProductServiceImpl.listCategories` 加降级（Redis 异常回源查库），`connect-timeout/timeout` 压到 2 秒。
- 部署侧：改为 docker-compose 的 `redis:7-alpine`（无 ports，AOF 落盘），密码 `SPRING_DATA_REDIS_PASSWORD`；`REDIS_HOST=redis`。
- 验证：服务器本机分类接口 8～12ms。
- access_token：user-service 原内存缓存 + order-service 新增订阅消息，会互相刷失效；抽到 `common/utils/WeChatAccessTokenProvider`（Redis 共享 + 10 秒锁 + 内存兜底），user-service 改用同一份。

### 3. 用户头像不显示
- 现象：`/api/user/profile` 返回 `http://api.jiemids.com/upload/avatar/...`；DevTools 因 HSTS 自动升级成 https，真机原生图片加载器不升级，头像空白。
- 根因：Spring Cloud Gateway 5.x 默认移除 `X-Forwarded-*`（未配 `trusted-proxies`），Nginx 的 `X-Forwarded-Proto: https` 到 user-service 时已丢，`UrlUtil` 回退到 request scheme=http。
- 修法：`UrlUtil.fullUrl` 对非本机 host 强制 https（本地 127.0.0.1 仍 http）；没有改网关代理配置。

### 4. 机器人头像不显示
- `static/zongjie.webp` 是包内本地 webp，微信 iOS 真机解码不稳定（`webp="true"` 只对网络图生效）。
- 转成 `static/zongjie.jpg`（512×477，42KB），4 处引用同步替换；旧 webp 可删。

### 5. 公网延迟
- 服务器本机接口 10～46ms（categories 10ms / home 46ms / products 15ms）；公网 0.8～2s。
- Redis 排查：命中率约 85%、事件循环 <1ms、无淘汰，**不是 Redis 瓶颈**。
- 瓶颈是 Cloudflare 跨境（HKG）+ 分类页串行两个请求（先分类、再商品）。
- 待定：Cloudflare 改 DNS-only（灰云）直连源站，国内预计降到 100～300ms；备选是小程序本地缓存分类 + CF Cache Rule。

## 订单未支付订阅消息（2026-10-25）

### 1. 模板与流程
- 模板 ID `_81DMBgBaFGYXqh9Cb6X41-32EfTF2zpqdSqFPXjzOg`，字段 `thing1` 商品名称 / `character_string5` 订单编号 / `amount7` 订单金额 / `time8` 订单失效时间。
- 提交订单（`pages/order/confirm.vue`）：同步校验 → `uni.requestSubscribeMessage`（必须在点击事件里、任何网络请求之前）→ `createOrder` 带上 `subscribeUnpaid` → 后端写 `is_subscribe_unpaid`。
- 不在页面 onLoad/onShow 弹窗；用户拒绝也继续下单。
- 推送时机：下单后第 20 分钟（30 分钟失效前 10 分钟），避免和超时关单任务竞争、`time8` 保持未来时间。
- 点击消息：`pages/order/pending-pay?orderNo=xxx`，`pending-pay.vue` 的 `onLoad` 读 orderNo 后只显示这一单；**不新建详情页**。

### 2. 后端实现
- `orders` 加 `is_subscribe_unpaid`、`unpaid_notify_sent` + `idx_orders_unpaid_notify`（生产库仍需手动 ALTER）。
- 定时任务 `sweepUnpaidNotify`：每分钟查 待付款 + 已授权 + 未推送 + `created_at <= now-20min`；二次校验状态/未过期后发送；成功或永久失败标记 `unpaid_notify_sent=1`。
- openid：order-service 新增 `UserMapper.findOpenidById`，同库查 `user.openid`（不跨服务 Feign）。
- 发送：`WeChatSubscribeMessageClient` 组 4 字段、调 `cgi-bin/message/subscribe/send`；40001/42001 清 token 重试一次；43101/47003/40003/40037 视为永久失败。
- 调度线程池 `spring.task.scheduling.pool.size=3`，微信慢调用不阻塞超时关单。

### 3. 上线顺序与测试
1. 生产库 ALTER `orders`（两列+索引）
2. 部署 order-service + user-service（common 的 token provider 改动）
3. 小程序发行（confirm.vue / pending-pay.vue）
- 测试可临时设 `WECHAT_NOTIFY_BEFORE_MINUTES=29`（下单后 1 分钟触发），验证完改回 10。
- 一次性订阅每测一次都要重新授权；`amount7` 若报 47003，尝试加币种符号 `¥`。

## 小程序提审驳回：未开通功能隐藏（2026-10-28）

### 结论
提审（2026-10-28 12:19:56）被拒，原因：「你的小程序服务【VIP】功能点击无响应，无法体验完整流程，请修复完善后再提交代码审核」。
VIP/优惠券/积分/用户协议/隐私政策当时都是 UI 占位，点击只弹「功能开发中」，审核按「不可用功能」处理。
处理原则：**未开通的功能不露出入口**，不是删代码，而是统一用开关隐藏；上线后改 `true` 即恢复。

### 实现（`e-commerce-uniapp`）
- 新增 `utils/features.js`：`vip / coupon / points / agreement / privacy` 五个开关，当前全部 `false`。
- `pages/my/my.vue`：VIP 金卡加 `v-if="features.vip"`；优惠券/积分 `url` 为空，由 `visibleStats` 过滤，卡片里只剩专属客服。
- `pages/my/about-us.vue`：用户协议/隐私政策按开关隐藏；「联系我们」改跳已有 `pages/my/customer-service`，不再弹「客服接入中」。
- `pages/order/review.vue`：隐藏「晒单最多可得 1 元优惠券」提示（优惠券未开通）。
- 已核对所有 `url: '/pages/...'` 跳转目标都在 `pages.json` 中，无空路由。

### 注意
- 占位 toast 不能替代隐藏：审核员看到入口就会点，点完没有完整流程即判「点击无响应」。
- 协议只是隐藏死链，微信后台「用户隐私保护指引」仍需配置；正式上线前最好补真实的用户协议/隐私政策页面。
- 恢复入口只改 `utils/features.js`，页面与样式代码都保留。

## 一键体验改为手机号验证码鉴权（2026-10-28）

- 原 `POST /api/shops/sandbox` 是公开接口（免登录白名单），游客不带凭证就能开沙箱店。
- 改为 `POST /api/admin/shops/sandbox`，必须先通过后台账号「手机号 + 验证码」登录拿到 admin 令牌：
  前端先调 `/api/auth/phone-login` 校验验证码，再带令牌开沙箱，成功后换成沙箱店令牌。
- 后端：删除公开的 `ShopController`；方法移到 `AdminShopController`；`WebMvcConfig` 免登录白名单移除该路径；网关只保留 `/api/admin/shops/**` 路由。
- 前端：`createSandbox` 改新路径；一键体验按钮改「一键体验（手机验证）」，未填手机号/验证码会提示并切到手机号 Tab；失败清理中间态 token。
- 生产限制：短信未接真实通道（`SMS_DEV_MODE` 默认 true），只有演示号 `13800138000` 能收到/回显验证码；要真正对外需接阿里云/腾讯云短信，并设置 `SMS_TEST_PHONE=` 关掉演示号。
- **2026-10-28 后续决策（面试演示改为单店）**：登录页移除「一键体验」入口，恢复账号密码 / 手机号验证码直接登录模板店（`shop_id=1`）；不再现场演示多租户隔离。后端沙箱接口与克隆逻辑保留但不再被前端调用，以后要恢复多租户演示时再把入口接回来。
