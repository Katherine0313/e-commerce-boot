# 发现与决策

> 2026-10-29 精简：只保留当前有效的约定、决策、风险和发布信息；已解决的历史 bug / 一次性操作流水已删除，需要时查 git 历史。

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


## 上传与文件路径（已定）

- 头像/商品图存相对路径，返回时拼完整 URL（换域名不失效）；类型白名单 png/jpeg/gif/webp，上限 2MB / 3MB，超限统一 413。
- 商品图路径 `/image/upload/N.webp`：`WebMvcConfig` 优先读 `upload/image/opt/`（压缩图），缺失回落原图；分类图同为 webp。
- **换商品图必须同时换 URL（加 `?v=` 版本号）** —— 这是本项目最贵的一个坑，已踩过一次：
  - `/image/**` 的响应头是 `Cache-Control: public, immutable, max-age=31536000`
    （`common/.../WebMvcConfig.java:84` 的 `CacheControl.maxAge(365, DAYS).cachePublic().immutable()`），
    `immutable` 明确告诉客户端「内容永不变化」，**一年内不再回源校验**。
  - 2026-10-31 09:03 真实照片**原地覆盖**了 136 个商品图文件（文件名没变 → URL 没变），
    于是 10-31 之前下载过这些 URL 的客户端就一直显示旧的占位图。
  - 微信小程序的图片/文件缓存**按微信账号分区**（同一手机换微信号进小程序，数据各自独立），
    所以「换个微信号看到的是新图」这个现象特别迷惑人 —— 实际上差的不是账号，是**客户端本地缓存**。
  - 修法：`products.image` 追加 `?v=<换图日期>`（脚本 `docs/sql/product-image-cache-version.sql`，
    本地 + 生产同一份，已执行）。机械表那批当时已经这么做过（`?v=20261030`），漏的是前 105 个。
  - **以后任何替换商品图文件的操作，都要顺手改 DB 里的 image 值加版本号**；
    只改文件不改 URL = 用户一年都看不到新图。`deploy/README.md` 里的「URL 刷新」指的是 EdgeOne 那一层，
    解决不了客户端自己缓存的那一份。
- 唯一遗留：头像上传目录仍硬编码 `upload/avatar/`，未抽成配置项（不阻塞）。

## 技术决策

| 决策 | 理由 |
|------|------|
| 按业务模块建包，模块内分 controller/service/mapper/entity/dto/vo | 数据多端共用，按端建包会复制粘贴 |
| C 端 `User*` / 后台 `Admin*` 前缀，路径 `/api/**` 与 `/api/admin/**` | 三端共用后端，只区分登录入口与字段裁剪 |
| 失败返回真实 HTTP 状态码，不恒 200 | 前端按 HTTP 语义判断，`Result` 只作结构补充 |
| 公共失败响应由 `OpenApiConfig` 全局注入，Controller 不写 `@ApiResponse` | 400/401/403/413/500 统一来源，避免重复注解 |
| 免登录清单唯一来源 `WebMvcConfig.EXCLUDE_AUTH_PATHS` | 拦截器豁免与文档补全读同一份，防漂移 |
| C 端商品浏览免登录，加购/下单/后台管理需对应令牌 | 首页不带 token 也能看商品；写操作走 user/admin 令牌 |
| 上传接口必须声明 `consumes = multipart/form-data` | 否则 springdoc 把请求体画成 JSON，413 注入也失效 |
| 不接入真实微信支付（2026-10-30） | 用户决定；`payOrder` 保持 mock 直接推进订单状态，不做 JSAPI/回调/查单/对账/`pay_record`，付费 VIP 一并取消 |

## 后台 Admin 对接契约

- 后台写操作走 `/api/admin/**`，`JwtInterceptor` 按路径分流 admin；混用 `/api/products` 会撞免登录白名单。
- 字段以驼峰为主，**商品模块例外**：`AdminProductVO/AdminProductDTO` 用 `@JsonNaming(SnakeCase)` 返回蛇形；订单/库存/用户统计为驼峰。
- 分页返回 `Result<PageVO<T>>`（list/total/page/pageSize），前端读 `res.data.data.*`。
- 时间字段 VO 用 `createdAt/updatedAt`，`LocalDateTime` 加 `@JsonFormat("yyyy-MM-dd HH:mm:ss")`。
- 日期筛选收 `dateField/startDate/endDate`，`dateField` 走白名单（created_at/pay_at/ship_at/finish_at）防注入。
- 售后审核只改主表 `status/merchant_remark/updated_at`，业务时间戳按状态写子表列（refunding/returning→review_at、rejected→rejected_at、**refunded→refund_at + refunded_at 同刻**、exchanging→exchange_at、exchanged→exchanged_at）；删除为主表/明细/子表 `is_deleted=1` 软删。**`refund_confirming` 已于 2026-11-03 删除**（阶段 29）：项目不接真实支付，没有「等款到账」时刻，「确认退款」是操作不是状态。
- **表名保持现状，不改成 `sys_user` 系（2026-11-01 决定）**：admin / admin_role / admin_user_role / admin_menu / admin_role_menu 五张表命名自洽、语义准确（admin = 后台账号，与 C 端 `user` 表天然区分）。评估过整套改成若依风格的 `sys_user`/`sys_role`/`sys_menu`，结论是不做：`admin` 在本项目同时承担「后台账号表」和「身份类型 admin（JWT type=admin、`/api/admin/**` 路径分流、Redis 失效标记 key、前端 token 守卫）」两套语义，连带改要动 107 个 Java 文件、956 处标识符、98 处前端请求路径，且 8 个服务 + 后台前端必须一次性发版（改错一环全线 401），收益却只有命名风格。**别再重复提这个改名方案。**
- **后台 token 改存 sessionStorage（2026-11-01 决定）**：`utils/token.js` 从 localStorage 改为 sessionStorage —— 关掉标签页/浏览器即清空，下次打开必须重新登录（后台管理系统安全要求：离开工位关页面就登出）。同一标签页刷新仍保持登录。**副作用**：sessionStorage 按标签页隔离，新开标签页也要重新登录（预期行为）。后端 JWT 有效期仍是 7 天，但 token 只活在会话里。「记住密码」（utils/remember.js）与之无关，仍用 localStorage 存 30 天。PC 商城与小程序（C 端）保持 localStorage 不变。

## 核心实现与口径

### 单店模式（2026-10-29）
- 多租户沙箱已彻底下线：无 `shop-service`、JWT 不含 `shopId`、18 张业务表无 `shop_id`、`shops` 表已删。
- 后台前端店铺名来自静态 `src/utils/shop.js`（`VITE_SHOP_NAME`，默认「杰米小店」）；小程序登录只传 `code`。
- 迁移脚本与执行顺序见 `docs/sql/README.md`；生产 MySQL 为 **5.7.44**，DDL 兼容 5.7。
- 本地库 2026-10-30 完成同一套迁移（预检查非主店数据全 0，未删业务数据）：18 表 `shop_id`、旧索引、`shops` 表已删，`uk_openid`/`uk_user_product`/`idx_favorites_user_time` 已重建。本地与生产逐项核对：25 张表 / 292 字段 / 89 索引完全一致。备份 `../backup_before_local_single_shop_2026-10-30.sql`。

### 首页
- `/api/home` 一次返回分类 + 猜你喜欢；分类走 Redis 缓存（单父分类一个 key，TTL 30 分钟，故障降级查库）。
- 热门推荐 `GET /api/products/hot?limit=8&excludeIds=`：优先 `categories.recommend=1`（含父分类推荐）的上架商品，随机排序；不足则随机上架商品补足，并排除已选/当前批次。小程序首屏与下拉刷新共用。
- 「猜你喜欢」调 Python `/recommend` 不存在（历史从未实现），当前一直走销量兜底；用户已决定近期不实现。

### 售后 / 审核改造与两张日志表（2026-11-03，阶段 29）

- **状态机只有一份真相**：`aftersale-service` 的 `support/AfterSaleTransitions`（10 条规则：类型 + 当前状态 + 目标状态 → operate_type + 按钮文案 + 附加条件）。
  后端 `review` 用它校验流转合法性、推导 `operate_type`；再用 `AfterSaleVO.availableActions` 把「当前能按哪几个按钮」下发给前端。
  **前端不再自己维护状态表** —— 原先后台三个 `*Review.vue` 各写一份下拉映射，是漂移的根源。
- **`operate_type` 与 `after_status` 一一对应**：枚举 `AfterSaleOperateTypeEnum`（9 项，code/tag/desc/logContent 四字段）只描述「做了什么」，
  「变成什么状态」由状态机给出，两者合起来唯一确定一条流水。
- **两张日志表用途完全不同，别混**：
  - `after_sale_log`（售后变更流水，**同步写、同一事务**）：买家和商家都要看，**不能缺条** → 切面必须在事务内层
    （`@EnableTransactionManagement(order = HIGHEST_PRECEDENCE)` 让事务切面在最外层 + 切面 `LOWEST_PRECEDENCE` 让它在最内层）。
  - `operation_log`（通用操作审计，**异步写**）：只给管理员看，丢了不影响业务 → 独立线程池，写失败只记 warn。
- **`content` 与 `merchant_remark` 分两列**：前者是买家可见的公开说明（枚举的 `logContent` 直接给出），
  后者是商家内部备注原文，**只有后台接口返回**。顺带修掉既有泄露：C 端 `AfterSaleVO` 原来也带 `merchantRemark`，现已不返回。
- **本项目不用 `@ConditionalOnMissingBean`**：各服务是 `@SpringBootApplication(scanBasePackages = "ecommerce")` **包扫描**装配，
  common 没有 `META-INF/spring/*.imports`，不是自动配置 jar → 该注解在普通 `@Component` 上求值时机不对，不可靠。
  要「可替换」语义，只能让业务服务自己声明一个 `OperationLogRecorder` bean 覆盖默认实现。
- **`JdbcOperationLogRecorder` 必须用 `ObjectProvider<JdbcTemplate>` 注入**：`file-service` 依赖 common 但**没有 JDBC 依赖**，
  直接注入 `JdbcTemplate` 会让它启动失败；用 `ObjectProvider` + `getIfAvailable()` 取不到就跳过写日志。
- **Boot 4.1 的 starter 改名**：`spring-boot-starter-aop` → **`spring-boot-starter-aspectj`**（本地 m2 只有后者，原写法会构建失败）。
- **`UserContext` 加 `CURRENT_TYPE` 必须一起清**：`remove()` 不清 `CURRENT_TYPE`，Tomcat 线程复用会把上一个请求的操作人带到下一个请求（串号）。
  因此 common 改动后**所有依赖 common 的服务都要重发**。
- **历史数据订正的顺序**：`refund_confirming` 订正时**子表先于主表** —— 主表一改成 `refunded`，子表语句就定位不到这批单了。
  `refunded_at` 取原 `refund_at`（与订单取消自动退款 `refund_at = refunded_at` 同刻的口径一致）。

### 订单 / 库存 / 营收
- 下单：同一事务内查商品 → 校验并扣库存（`stock >= quantity` 乐观锁）→ 写订单/明细 → 清购物车；Redis 幂等键防重复下单。
- 状态机：1 待付款 → 3 待发货 → 4 已发货 → 5 已完成，6 已取消；取消/超时恢复库存，已支付未发货取消自动生成整单退款售后单。
  - **2 待确认收款已退役**（2026-11-04）：那是原 Node 真实支付场景的「商家人工确认到账」，本项目支付是 mock 的，点一下即成功，不存在到账延迟，这一环纯属人工卡点——生产库曾积压 63 单卡在 2，商家不逐单点「确认通过」订单就永远进不了发货。现改为**支付成功直接进 3**，code 位保留不动（2 号空出来不用），存量已迁移，见 `docs/sql/order-status-merge-pending-confirm.sql`。
  - 副作用：`orders.pay_at` 原先只在 status=2 写入，合并后改到 status=3 写入——漏改会让营业额统计归零。
  - **`orders.confirm_at` 已删列**（2026-11-04，`docs/sql/drop-confirm-at.sql`，本地 + 生产均已执行）：它原本是「商家确认收款时间」，状态 2 退役后失去语义；合并时曾临时复用它表示「进入待发货的时间」，现直接删掉，不留残影。
  - 副作用：PC 商城「确认收货」原先误调 `/orders/{id}/confirm`（那是确认收款接口，已删），已改调 `/orders/{id}/finish`。
- 定时任务：每分钟扫超时待付款订单取消并恢复库存；发货满 7 天自动完成。
- 营业额口径：实收（`pay_at` 区间 `pay_amount` 合计）− 退款（refunded 售后 `refund_amount`）；退款跟原订单 `pay_at` 归属；实收/退款都含取消订单。工作台今日/昨日按日期入参。
- 按天统计必须 `GROUP BY DATE_FORMAT(pay_at, '%Y-%m-%d')`，不能直接 `GROUP BY pay_at`；条件带 `is_deleted=0`。
- 未支付提醒：下单时用户授权 → 第 20 分钟一次性订阅消息 → 点击跳 `pending-pay?orderNo`；access_token 走 Redis 共享缓存。
- 物流轨迹是**假数据**（不接真实物流 API，与「支付也是 mock」同理）：快递公司 + 单号是发货时写入 `orders` 的真实值，
  轨迹节点按订单 id 当种子编。轨迹生成只有一份：`order-service/support/LogisticsBuilder`（Spring Bean）。
  - **三端同一份**：后台管理端 / 小程序 / PC 商城都走这一个实现，轨迹内容与时间戳逐字一致 ——
    不会出现「商家这边已送达、顾客那边还在派送中」。快递公司名也统一读字典表 `delivery_express`
    （`LogisticsBuilder.courierName`），商家在后台改名三端同步生效。
  - **滚动时间轴 + 整点基准**（2026-11-04 改）：节点时间 = `now(整点) - 6h/6h/6h/4h`（已完成再加 派送中 -2h、已签收 finishAt）。
    「整点」是必须的：同一小时内所有请求返回完全相同的时间戳，两次请求跨一秒就会差一秒、三端就对不上了；
    跨整点自动推进一次。早先的「固定偏移（shipTime + 2h/6h/18h）」会在发完一段时间后卡住不再更新。
  - 后台入口在订单详情弹窗 + 快递发货列表（`LogisticsDrawer.vue`），**「发货流水」是操作流水（order_delivery_log），不是轨迹**。

### 售后 / 收藏 / 反馈
- 售后主表无 `user_id`，C 端归属靠 `LEFT JOIN orders` 的 `o.user_id`；子表按 `after_sale_id` 关联。
- 收藏唯一键 `(user_id, product_id)`，`ON DUPLICATE KEY UPDATE id=id` 幂等；下架商品仍返回（前端打标）。
- 反馈类型白名单 suggestion/product/order/other，内容 5~1000 字，截图最多 6 张且必须是 `/image/feedback/` 相对路径。

### 商品属性 / 分类扩容 / C 端分页（2026-10-29）
- `products.specs`：JSON **有序数组** `[{"name":"屏幕","value":"6.1英寸"}]`；实体 `String`，VO/DTO 用 `List<SpecItemVO/DTO>`，`ProductSpecsCodec` 编解码；与 `description`（文案）、`body`（富文本）并列，不合并。
- 分类重构为 **8 个一级 / 21 个二级**：id=4 改名「耳机音箱」；新增 7「数码配件」（701~704）、8「智能家居」（801~804）；`listOnSaleByCategory` 改为 `category_id IN (SELECT id ... id=? OR parent_id=?)`，一级/二级都能过滤。原 502「运动手表」与 501「智能手表」边界模糊（种子按叶子轮询，导致 Watch Ultra/Ultra 2、GT 4/GT 5 同系列拆开），已用 `09-merge-watch-category.sql` 合并到 501；随后用 `10-mechanical-watches.sql` 新增 503「机械普通手表」（传统指针表：机械/石英）和 10 款商品 id 127~136。
- 商品整理为 **126 件**：现有 50 个保留并补 specs，新增 id 51~126（`SEED-{id}`，按型号去重，图片 `{id}.webp`）；每个叶子 6 个；旧版 51~300 的「标准版/增强版」伪变体通过 `03a-remove-old-seed.sql` 清理；脚本在 `docs/sql/product-expand/`，生成器 `generator/generate_catalog.py`。
- 新接口 `GET /api/products/page?page=&pageSize=&categoryId=&keyword=&brand=&minPrice=&maxPrice=&sort=`，返回 `PageVO<ProductVO>`；pageSize clamp 60，sort 白名单（default/newest/price_asc/price_desc）；旧 `/api/products` 仍返回数组，仅分类过滤增强，兼容小程序/PC。
- 2026-10-30 前端接入分页：小程序分类页（`@scrolltolower`，每页 12，首屏缓存 60s）与搜索页（`onReachBottom`，每页 12）、PC 商城列表/搜索（“加载更多”，每页 40）全部改用 `/api/products/page`；老 `/api/products` 的三条 SQL（`listOnSale`/`listOnSaleByCategory`/`searchOnSale`）统一加 `LIMIT 200` 兜底，避免旧客户端全量拉取。
- 后台 `listAdmin/countAdmin` 分类筛选也支持一级/二级；`AdminProductVO/DTO` 增加 specs（实际是驼峰，勿加 SnakeCase）。
- 分类缓存新增 `CategoryCacheEvictor`，后台分类/商品写操作后清 `category:parent:*`。
- `products.description` 重命名为 `title`（中文叫**主标题**，`sub_title` 保留）；后端实体/VO/DTO/Mapper 全部改为 title，order-service 商品快照用 `title AS description` 兼容购物车表。
- 主标题内容为 SEO 长标题：名称（含品牌+型号+品类）+ 卖家卖点 + 3 个关键参数 + 官方正品/现货速发；脚本 `05-fix-titles.sql`，生成器 `fix_titles.py`。
- `sub_title` 采用淘宝风格：一句话 21~28 字，销量/加购营销数字 + 可开发票 + 品类口碑短语，三段用 ` | ` 分隔（中文逗号在列表卡片里太碎）；脚本 `07-fix-subtitles.sql`，生成器 `fix_subtitles.py`。数字按商品 id 稳定生成（演示用，非真实销量）。
- 商品真实图转换：桌面原图 `{id}.png` → 主图 `upload/image/upload/{id}.webp`（800×800，非正方形等比+白底）与 `opt/upload/{id}.webp`（400×400）；`{id}-1/2.png` → `upload/image/body/{id}-1.webp`（最长边 1200）。脚本 `generator/convert_product_images.py`。51~105 共 165 张，522MB → 9.3MB。`WebMvcConfig` 优先返回 opt，所以接口里 `/image/upload/{id}.webp` 实际是 400px。
- 生成器 `kind_of` 原来只认型号里带「鼠标」二字，`MX Master 3S` 被判成 generic，名称/标题/参数全落成兜底「商品」+ 手机壳模板；已补型号兜底，并用 `08-fix-seed-data.sql` 修正 id=85（名称/标题/参数）和 id=76（title 里混入的脏字符 `c`）。
- 本地与生产均已执行数据脚本并验证：8/21/126、每叶子 6、商品名全唯一、specs 无空值、分页/筛选/排序/后台 specs 读写均通过。生产备份 `/www/backup/e_commerce_db_before_product_expand_2026-10-29_1935.sql`；生产额外有一个空的「家电 / 电冰箱」分类，用户自行删除。

### 促销模块（2026-10-30）
- 设计依据：黑马《Java生鲜电商平台促销模块架构设计》的三层改价模型——**改商品价 → 改商品小计 → 改订单金额**；v1 实现直减类（限时折扣 / 新用户专享）与满减类（满额减 / 满额折），**拼团不做**，**秒杀列为后续规划**（库存扣减/并发模型不同）。
- 表：`promotion`（活动）、`promotion_item`（商品/活动价）、`promotion_rule`（满减阶梯）、`promotion_usage`（使用记录）；`orders` 加 `original_amount/discount_amount`，`order_items` 加 `original_price/discount_amount`。脚本 `docs/sql/promotion/01-promotion-schema.sql`，**尚未执行**。
- 活动状态不落库：未开始/进行中/已结束由 `start_time/end_time` + Java 传入的 now 推导（MySQL 是 UTC，禁止用 NOW()）；`manual_status=1` 仅表示运营提前结束。因此**不需要定时下架任务**，也不改 `products.sales_price`。
- 算价引擎 `common/promotion/PromotionCalculator`：纯函数，两个服务共用。规则：同一商品命中多个直减活动取活动价最低（同价取 id 小）；满减全局只应用优惠最大的一个，按小计占比分摊到行（末行承接舍入残差）；同类型互斥、不同类型叠加；新用户专享按 `orders.pay_at IS NOT NULL` 判定。
- 后台 API：`/api/admin/promotions` CRUD + `/{id}/end` + `/{id}/stats`；仅未开始可编辑/删除，进行中只能提前结束；直减类同商品时间重叠校验。
- 订单侧：`POST /api/orders/quote` 实时报价（不落库）；购物车只展示直减价；下单在 `OrderServiceImpl.doCreateOrder` 重新算价并落快照，写 `promotion_usage`（order-service 跨域写 product 域表，已和用户确认接受）。
- 前端口径：C 端 `ProductVO` 新增 `originalPrice/promotionTag`（`price` 为活动价）；小程序商品卡片/详情/购物车/确认页/订单页展示标签、划线价与优惠；确认页金额 = 报价应付 + 保险（保险不参与促销）；PC 商城卡片/详情同样展示；后台促销页重写 `OnSale.vue`，订单列表加「优惠金额」列。
- 售后：`order_items.sub_total` 存的是优惠后金额（满减已分摊到行），现有按 `sub_total` 全额退款的口径可直接复用。

### 优惠券模块（2026-10-30）
- 归属：**order-service**（券模板 / 用户券 / 核销都在订单域，保证下单、支付、取消、退款同事务）；后台菜单放「经营工具」下，与「促销活动」平级。表：`coupon_template`、`coupon_scope`、`user_coupon`；`orders` 增加 `coupon_id/coupon_name/coupon_amount`。脚本 `docs/sql/coupon/01-coupon-schema.sql`，**尚未执行**。
- 规则：满减券/折扣券（可封顶）；范围全场/分类/商品（分类含子分类）；有效期固定时段或领取后 N 天；每人限领 1 张（唯一键兜底）；一笔订单一张券；券与直减/满减**可叠加**，券门槛按促销后金额判断，保险不参与优惠。
- 算价：`CouponPricingService` 在促销报价上叠加券，券优惠按适用商品占比分摊到行（`order_items.discount_amount` 含券分摊，`sub_total` 为券后金额，售后按行退款口径不变）。
- 状态：模板未开始/发放中/已结束/已停止（时间 + 剩余量推导，停止用 `manual_status=1`）；用户券 UNUSED/LOCKED/USED，过期读时推导，无定时清理任务。
- 并发：领券唯一键 + 原子扣减剩余；下单先算券再 `UPDATE ... status=UNUSED` 原子锁券（失败回滚）；支付 LOCKED→USED；取消/超时退回；整单退款（取消已支付订单）在有效期内返还已用券。**后台售后退款暂未返还券**，留 P1.5。
- 接口：C 端 `/api/coupons/center`、`/{templateId}/claim`、`/mine`、`/available`；后台 `/api/admin/coupons` CRUD + `/{id}/stop`、`/{id}/grant`、`/{id}/records`；`POST /api/orders/quote` 与下单 `couponId` 已接入。
- 前端：后台 `pages/business/coupon/index.vue`（模板 CRUD、停止、定向/全量发券、领取记录），促销页迁到 `pages/business/promotion/index.vue`，菜单为「经营工具 → 促销活动 / 优惠券」；小程序新增「我的优惠券」「领券中心」，确认页自动选最优券并可切换，订单页展示优惠合计与券优惠；`utils/features.js` 的 `coupon` 已置 true。ecommerce-mall 本期未动。
- **C 端券状态有 5 种**（`CouponServiceImpl.userCouponStatus`）：`AVAILABLE`（可使用）、`NOT_STARTED`（未生效，固定有效期还没到）、`LOCKED`（使用中，下单锁券未支付）、`USED`、`EXPIRED`。小程序原来只按 `AVAILABLE/USED/EXPIRED` 三个页签 + 后端按状态过滤，**`NOT_STARTED`/`LOCKED` 的券在所有页签里都查不到** —— 后台发到手的券会「凭空消失」（2026-11-01 修：一次拉全量，页签本地过滤，「可使用」页签收纳 `AVAILABLE/NOT_STARTED/LOCKED`，卡片上再标出未生效/使用中）。
- **MySQL 秒级进位的小坑**：`buildUserCoupon` 把带纳秒的 `LocalDateTime.now()` 写进 `DATETIME(0)`，MySQL 四舍五入到整秒 → 刚发的券 `start_time` 可能比真实时间晚 1 秒 → 状态算成 `NOT_STARTED`（实测复现，1 秒后自动变可使用）。影响很小，但排查时别误判成「券发早了」。
- **定向发券必须校验用户存在**：后台填错用户 ID 时，原来照样发一张券（`granted=1`），券却没人看得到，运营还会以为发成功了。2026-11-01 加 `CouponMapper.listExistingUserIds`，发放前校验，不存在直接 400「用户 ID 不存在：xxx」（后台全量发券不受影响）。
- **券数量口径**：小程序「我的」页的优惠券数量 = 还没用掉的券（`AVAILABLE + NOT_STARTED + LOCKED`），与「我的优惠券」的「可使用」页签一致；只数 `AVAILABLE` 会让后台刚发的券在数量上看不出来。

### Banner 与文章模块（2026-11-01）

- **归属**：product-service（内容域，跟商品放一起）。表 `promotion_banner`、`promotion_article_category`、`promotion_article`，脚本 `docs/sql/promotion/02-banner-article-schema.sql`（幂等，可重复执行）。
- **约定对齐本项目**：软删列 `is_deleted`（无 `deleted`）、只有 `create_time`/`update_time`（无 creator/updater/tenant_id）、排序列 `sort_order`、状态 `status` 0/1、布尔用 tinyint、`utf8mb4_general_ci`（生产 MySQL 5.7 不支持 0900_ai_ci）。
- **枚举放代码常量**：项目**没有字典表**，`position`（1 小程序首页 / 2 小程序分类页 / 11 PC 商城首页）、`link_type`（0 不跳转 / 1 商品 / 2 文章 / 3 外链）统一放 `BannerConstants`，不建 `parameters` 字典。
- **Banner 一条记录只服务一个位置**：终端由 `position` 决定，所以只存一张图（不搞双图字段）；`link_type`+`link_value` 拆开，商品/文章存 id、外链存 URL。
- **文章标题全量唯一**（含逻辑删除的记录）：小程序按标题查详情（「用户协议」「隐私政策」），重名会查错，所以校验和唯一索引都按全量算，删过的标题也占着。
- **文章分类不单独建菜单**：分类的增删改复用文章权限（后台页面里一个「分类管理」弹窗搞定）。
- **JSON 用 camelCase**：本模块后台与 C 端都 camelCase（只有 `AdminProductDTO/VO` 用 `@JsonNaming(SnakeCase)`），PC 商城与小程序能共用同一份接口。
- **接口**：C 端 `GET /api/banners?position=`、`GET /api/articles`、`/api/articles/{id}`、`/api/articles/by-title?title=`、`POST /api/{banners|articles}/{id}/browse`（浏览量 +1）；后台 `/api/admin/banners`、`/api/admin/articles`、`/api/admin/article-categories`（各带 `{id}/status` 启停）。
- **免登录白名单**：`common/WebMvcConfig.EXCLUDE_AUTH_PATHS` 加 `/api/banners/**`、`/api/articles/**`（只读 + 浏览量统计，PC 商城/小程序不需要登录）。
- **网关必须同步**：gateway `application.yml` product-service 的 Path 列表要补全 Banner/文章路径，否则落到 `/api/**` 通配被 user-service 接走（404）。
- **上传接口按 type 分支鉴权**：`/api/admin/upload?type=` —— `banner`→`business:banner:add`、`article`→`business:article:add`、`body`→`product:product:add` **或** `business:article:add`（正文图商品详情与文章正文共用 type=body，任一编辑权限即可传）。超管 `*:*:*` 直接放行。
- **浏览量统计口径**：后台预览**不** +1，只在 C 端浏览接口 +1；文章浏览量与 Banner 点击量分开统计。
- **前端**：后台 `pages/business/banner/BannerList.vue`、`pages/business/article/ArticleList.vue` + `ArticleCategoryModal.vue`；PC 商城 `Home.vue` 轮播改接口驱动（接口无数据时回落静态图，避免上线即空白）、新增 `views/article/ArticleDetail.vue`、页脚协议按标题进文章；小程序首页/分类页轮播改接口驱动，`components/custom-swiper.vue` 同时兼容字符串 URL 与对象 item（`item.src`/`item.image`），新增 `pages/article/detail.vue` 用 `rich-text` 渲染正文并把相对图片路径补成绝对地址。
- **菜单与授权**：`admin_menu` 503/504 + 按钮 540~542、550~552；按用户决定把新菜单授权给**运营角色（role_id=2）**，与既有「经营工具」下的促销活动/优惠券/会员归属一致；店铺管理员是超管（`*:*:*`）不用授权。
- **踩坑**：Windows 的 mysql 客户端默认 GBK，导 UTF-8 的 SQL 必须加 `--default-character-set=utf8mb4`；Git Bash 的 curl 发中文 JSON 会变 GBK 触发「请求参数格式错误」，校验类接口要用 Python `urllib`（UTF-8）调。

### 商品图片体积优化（2026-10-30）
- 审计发现 `opt/upload` 缺 33~50 共 18 张缩略图（回落到 800px 原图，50.webp 186KB），另有 9/12/17/21/22/31/32 共 7 张 opt 超过 30KB。
- 新增 `docs/sql/product-expand/generator/rebuild_opt_images.py`：从 `upload/image/upload/{id}.webp` 生成 400×400 白底 opt，quality 74、method 6；已补齐 18 张并重压 7 张，现在 136 张 opt 全部存在且均 ≤30KB（多数 5~18KB），50.webp 186KB → 8.6KB。图片已 scp 到生产 `/www/java/upload/image/opt/upload/`。
- `WebMvcConfig` 图片缓存头由 `max-age=7d` 改为 `max-age=365d, immutable`（需重新发布 product-service 生效）。
- 注意：CDN（**腾讯云 EdgeOne**）按 URL 缓存，33~50 之前缓存的是原图、7 张大图缓存的是旧 opt，**必须刷新这 25 个 URL** 后才会命中新文件；之后可 prewarm 全部图片。
- 测速结论：内网 `/api/home` 79ms、`/api/products/hot` 13ms，公网均 ~1.4s；Cloudflare HIT 的 10.9KB 图片 TTFB 仍 1.65s → 瓶颈是 Cloudflare 香港跨境链路，不是后端/Redis。图片/API 若要明显变快，需图片走国内 CDN（COS+CDN 或 CDN 回源源站，源站 IP 仍隐藏），API 可评估 EdgeOne/全站加速或 Cloudflare China；灰云会暴露源站 IP，不采用。

### 图片规格表（2026-11-02）

> **项目约定：后台上传的每一类图片都有固定的目标规格，规格表是唯一出处** —— `e-commerce/src/utils/image-spec.js`。
> 以后新增图片类型或改展示位，必须改这张表，不要在页面里写死尺寸。

- **为什么要按 type 分**：以前所有图片共用一套「最长边 1200」（`compressImage.js` 里的硬编码 `MAX_EDGE`），但展示位差别很大 —— banner 是宽幅轮播坑、商品卡是小方图、正文图是长图。尺寸不贴展示位 = 白下载一堆像素 + 画幅不对被 `object-fit:cover` / `aspectFill` 裁掉内容。
- **目标尺寸 = 展示像素 × 2（retina）**，逐个核实过：

  | type | 目标尺寸 | 适配 | 推导依据 |
  |---|---|---|---|
  | `banner` position=1 小程序首页 | 750×375 | cover | `custom-swiper` 默认 360rpx=180px 高、满屏 375px 宽 |
  | `banner` position=2 小程序分类页 | 750×200 | cover | `category.vue` 传 `height="200rpx"`=100px 高 |
  | `banner` position=11 PC 首页 | 1600×700 | cover | `Home.vue` hero 高 350px、`.banner` 宽 ≈798px |
  | `article` 封面 | 1200×675 | cover | PC 详情页 `.article-cover{max-width:100%}` 铺满 1200 容器 |
  | `category` 分类图 | 400×400 | cover | 分类小方图 |
  | `upload` 商品主图 | 800×800 | contain + 白底 | 与 python 脚本 `to_square()` 口径一致（历史 136 个商品图同规格） |
  | `body` 正文图 | 最长边 1200 | contain | 富文本满宽展示 |
  | `after-sale` 凭证图 | 最长边 1200 | contain | 列表缩略 88px，但点开预览要看清晰 |
  | `review` 评价晒图 | 最长边 800 | contain | 详情页缩略图只有 ~100px，点开看大图，800 足够 |

- **两种适配**：`cover` = 填满目标画幅、按较大缩放比裁掉多余部分、裁切居中（画幅正确优先）；`contain` = 等比缩放塞进目标画布、居中留白、不裁不放大（照片类不能裁内容）。
- **统一输出 webp**（quality 0.82）：同画质比 jpeg 小约 30%，且**保留 PNG 透明通道**（转 jpeg 会把分类图标这类透明图压成黑底）。gif 跳过（webp 不支持动图）。浏览器不支持 webp 编码时 `toBlob` 会按规范静默回落成 `image/png` —— 必须校验 `blob.type`，否则会白传 png。
- **一个 banner 只服务一个位置**（沿用阶段 25 的结论），所以规格按 `position` 分档；切换位置后原图画幅就不对了，页面会提示重新上传。
- **方形图传进宽幅位会被裁**：1200×1200 传小程序首页位（750×375）会上下各切 275px。运营必须传宽幅图，表单上的「建议尺寸」提示就是给这个用的。
- **后端只做体积兜底**（`UploadServiceImpl`）：图片 5MB / 视频 50MB，防绕过前端直传大图。**后端不做图像处理** —— 压缩全在前端 canvas，后端零图片依赖。
- **C 端上传不重复造**：小程序 review / feedback / after-sale 走微信原生 `uni.chooseImage({sizeType:['compressed']})`，已经压过了。
- **实测收益**：用现网那张 1200×1200 banner 编码实测，小程序首页位 58.2KB → 21.7KB，像素 144 万 → 28 万（解码内存 5.76MB → 1.1MB）。方形图裁进宽幅位是设计取舍，画幅正确比清晰度更重要。

### 后台商品评价管理模块（2026-11-02）

> 完整方案见仓库根目录 `review`（v3，已按实际实现回改）。这里只记**约定和坑**。

- **表在 order-service**：`product_reviews` 属于订单域，`ProductReview` 实体 + Mapper + `ReviewService` 都在 order-service，
  后台模块跟表在一起，不用跨服务调用。所有服务连同一个库 `e_commerce_db`，**join `orders`/`user`/`admin` 都合法**。
- **复用同义列，别加重复字段**：`status`（1 显示 0 隐藏）就是需求里的 `visible`；
  `is_deleted` 就是 `del_flag`；`updated_at` 就是 `update_time`。只新增 4 列：
  `reply` varchar(1000) / `reply_time` datetime / `is_virtual` tinyint / `created_by` bigint。
- **需求说「已 ALTER 过」是错的**：2026-11-02 核对本地与生产 `SHOW COLUMNS`，两边结构完全一致、从未 ALTER 过。
  生产是 **MySQL 5.7**，不支持 `ADD COLUMN IF NOT EXISTS` → 脚本必须用 `information_schema` + `PREPARE/EXECUTE` 幂等写法。
- **C 端路径不能挂 `/api/products/{id}/reviews`**：网关里 product-service 规则**排在 order-service 前面**，
  会被抢走（评价表在 order-service，走不通）→ 用 `/api/reviews/product/{productId}`，
  C 端路径**不要**以 `/api/products` 开头。
- **真实评价「整条只读」**：Q4 拍板的不只是锁内容字段，而是**只能回复/隐藏/删除**，内容/星级/标签/图片一律不能改。
  理由：事后改动买家原话等于伪造消费者评价，比隐藏更严重。
  实现上**双保险**：Service 抛 400「买家真实评价不可编辑」 + SQL `WHERE id=# AND is_deleted=0 AND is_virtual=1`。
  前端只是把按钮禁用，真正的拦截在后端。
- **虚拟评价不挂真实订单/用户**：`order_id=0`、`user_id=0`、`created_by=admin.id`；
  商品名与图**后端从 `products` 表快照取，不信任前端传值**。
  安全前提：买家端防重复提交用 `findByOrderId`（`order_id >= 1`），`order_id=0` **永远不会撞上**。
- **`created_by` 存的是 admin.id**，跟 C 端 `user.id` 是两套 ID 空间。
  `UserContext.getUserId()` 在后台请求里就是 admin.id，在 C 端请求里是 user.id —— 同名不同义，别混用。
- **一条评价只保留一条回复**：再次回复 = 覆盖旧回复 + 刷新 `reply_time`（不做多条回复/追问流）。
  回复正文最长 1000 字（对应 `reply varchar(1000)`）。
- **时间戳一律 Java 传参**（`LocalDateTime.now()`），**不要 MySQL `NOW()`** —— 服务器时区是 UTC，差 8 小时。
  后台列表的日期区间筛选也照 `AdminOrderServiceImpl.parseDateTime()` 解析 `yyyy-MM-dd HH:mm`。
- **后台列表筛选用 `<if>` 动态拼**：空值不拼条件（不是拼 `AND 1=1`）；`countAdmin` 与 `listAdmin` **必须同筛选条件**，
  否则分页总数对不上。虚拟评价 `order_id=0` → LEFT JOIN orders 拿不到订单号，前端显示「—」，也筛不出来（符合预期）。
- **C 端只返回 `status=1 AND is_deleted=0`** → 后台一隐藏/一删除，C 端立刻不显示（这就是「显示/隐藏」功能的闭环）。
  买家端 `findByOrderId` 已带 `is_deleted=0`，后台删了评价**不影响**买家「该订单已评价」的判断（不能重复评价）—— 这是合理的。
- **C 端必须脱敏昵称**：`user.name` 若是 11 位纯数字（被填成手机号），后端打码成 `138****8000`；
  虚拟评价 `userName="系统"`、`userAvatar=null`、前端打「系统评价」标签（《网络交易监督管理办法》要求区分标注系统评价）。
- **C 端不暴露「有几条是系统评价」**：Summary VO 里刻意**没有** `realCount`/`virtualCount`，
  星级分布用扁平字段 `fiveStar…oneStar`（前端直接取，不用遍历 map）。
- **详情页三块顺序（已定）**：**商品评价 → 商品参数 → 图文详情**。评价是社会认同信号、影响跳出率，放最前；
  参数扫一眼就过；图文详情最费时间，放最后。小程序与 PC 商城一致。
- **评价区默认只露 3 条**：接口一次拉 20 条（点开「查看全部评价」不用再请求），但展示层只 slice 前 3 条 ——
  评价区排在详情页最前面，一上来铺 20 条会把图文详情顶没，等于把黄金位置让给评价。
  不用 `onReachBottom`（商品详情页已有其它滚动逻辑）。
- **一条评价最多一个视频** → 视频**不进**图片九宫格（`<img>` 播不了 mp4），单独一行 `<video controls>`；
  后台列表的视频也不进缩略图，只标一个「含视频」标签。
- **虚拟评价新增/编辑都只允许虚拟评价**：`product:review:add` / `product:review:edit`。
  上传接口 `/api/admin/upload?type=review` 要给这两个权限（`AdminUploadController` 加 `case "review"`）。
- **导出 Excel**：order-service 已依赖 `poi-ooxml`，不用加依赖。样板 `ReportServiceImpl.exportOperationReportByTemplate()`。
  必做两件事：RFC 5987 文件名编码（中文名 + `filename*=UTF-8''`）、**公式注入防护**
  （单元格以 `= + - @` 开头就加 `'` 前缀）。超过 5000 条截断。
- **菜单与授权**：`admin_menu` id **202**「商品评价」挂 parent_id=3（订单管理），按钮 230~234；
  `admin_role_menu` 给 role 2（运营）全授权、role 3（客服）只给菜单 + 回复。
  **注意 `admin_menu` 表不驱动前端菜单渲染** —— 前端菜单是 `router/index.js` 静态表 + `buildMenu()` 按 `meta.permission` 过滤，
  **两处都要改**，`path` 和 `perms` 必须一致，否则路由守卫会把页面直接跳走。
- **免登录白名单**：C 端读接口放 `common/WebMvcConfig.EXCLUDE_AUTH_PATHS`（`/api/reviews`、`/api/reviews/**`）。
- **踩坑**：Windows 的 mysql 客户端默认 GBK，导 UTF-8 的 SQL 要加 `--default-character-set=utf8mb4`；
  Git Bash 的 curl 发中文 JSON 会变 GBK 触发「请求参数格式错误」，校验类接口用 Python `urllib`（UTF-8）调。
  Vue 模板里 `:fallback` 这种**双引号属性里塞 `\"` 转义**会直接编译失败（Unterminated string constant），
  SVG 占位图要放 script 级 `const` 再用 `:fallback="PLACEHOLDER"`。
- **生产发布（2026-11-02，已完成）**：备份 `/www/backup/e_commerce_db_2026-11-02_1227.sql`（302K）→
  两份 SQL 走 **stdin 管道**导入（服务器不落文件）→ `release.sh order-service` + `release.sh gateway`
  （镜像 `20261102-83aa72c-dirty`）→ `release-web.sh`（`index-87362cae.js`）→ `release-mall.sh`
  → 小程序 HBuilderX 12:55 导出 `unpackage/dist/build/mp-weixin`。
- **`release.sh` 的 tag 后缀 `-dirty` 表示构建时工作区有未提交改动**：本次 order-service / gateway 带 `-dirty`
  （评价模块的新文件还没提交），**product-service 生产镜像是 clean 的 `20261102-83aa72c`（= HEAD）**。
  后果：`AdminUploadController` 加的 `case "review"` 分支**没上线**，线上走 `default` 分支要求 `product:product:add`
  —— 运营角色本来就有这个权限，**功能不受影响**，只是权限口径不精确，下次发 product-service 顺带上。
- **生产验证只做只读**：写操作（回复/隐藏/删除/新增虚拟评价）**故意不跑** —— 生产 `product_reviews` 是真实买家评价，
  改线上数据不在授权范围；这些路径已在本地全链路实测。验证脚本只输出 HTTP 状态码、条数、**字段名列表**和
  PASS/FAIL 布尔断言，**不打印客户昵称/订单号等业务数据**（订单号筛选用脚本现查一条真实 `order_no` 再比对）。

### 后台 RBAC P0（2026-10-30）
- 表：`admin_role` / `admin_menu` / `admin_user_role` / `admin_role_menu`；`admin` 仍是账号表（没有 `admin_user`）。脚本 `docs/sql/rbac/01-rbac-schema.sql`，本地与生产均已执行（生产备份 `/www/backup/e_commerce_db_before_rbac_2026-10-30_1452.sql`）。
- 初始 4 角色：`admin` 店铺管理员（超级权限）、`operator` 运营、`service` 客服、`warehouse` 仓储发货；演示账号 operator/operator123、service/service123、warehouse/warehouse123。
- 权限码 `模块:实体:动作`（如 `product:product:list`、`order:order:ship`）；菜单类型 M 目录 / C 菜单 / F 按钮；超级管理员在 `AuthServiceImpl` 里直接返回 `*:*:*` 和全部菜单。
- 登录：`AuthServiceImpl` 读角色/权限/菜单树，放进 `AdminUserVO`，并把 `roles/permissions` 写入 JWT claims；旧 `admin` 账号 `role=admin` 仍按超管兜底。
- 校验：`common` 新增 `@RequirePerm` + `PermissionInterceptor`（排在 JwtInterceptor 后），从 `UserContext` 读 JWT 里的权限，无权限返回 403；各服务的 `/api/admin/**` 已按类/方法加注解读取/写权限。
- 前端：`utils/auth.js` 增加 `hasPermi/isAdminToken`；`v-permission` 按钮指令；`App.vue` 按 `meta.permission` 生成过滤后的 stepin 菜单；路由守卫拦截无权限直链；订单发货/取消/删除、售后审核、商品/分类/标签/库存、促销/优惠券/会员等按钮已接入。
- 验证：operator 访问 `/api/admin/products` 200，warehouse/service 403；C 端 `/api/products/page` 无 token 200；operator 登录拿到 32 个权限。
- P1 已完成（2026-10-30）：后台「系统管理」下新增「管理员账号」「角色管理」两个页面；
  - 账号：分页/关键词/状态筛选、新增、编辑（姓名/手机号/状态/角色）、重置密码；超级管理员账号不能禁用、不能移除 admin 角色、不能禁用自己。
  - 角色：角色 CRUD（`admin` 角色不可删/不可停用、有账号使用中不可删）、菜单树勾选分配（保存时自动补父菜单）。
  - 密码：新增/重置统一 BCrypt（`spring-security-crypto`）；历史明文密码登录成功后自动升级；登录兼容 BCrypt 与旧明文。
  - API：`/api/admin/system/users`、`/roles`、`/menus/tree`，分别用 `system:user:*` / `system:role:*` / `system:role:assign` 权限。
  - 实测：admin 访问 system 接口 200，operator 403；新增账号落库为 `$2a$10$...` 且可用新密码登录；角色菜单分配可保存。
- P1 剩余（2026-10-30 更新）：菜单管理 CRUD、角色/菜单变更强制下线已完成（见下节）；剩操作日志/登录日志、菜单动态路由（暂缓）、清理生产演示账号。
- 发布顺序：**auth-service 先发**（新令牌才带 permissions），再发 product/order/aftersale/user/gateway，最后后台 dist；旧 token 需要重新登录。

### 管理员姓名语义修正（2026-11-01）
- **语义约定（别再填错）**：`admin.name` = **管理员本人姓名**（这条记录就是登录人）；`admin_role.role_name` = **角色名**（运营 / 客服 / 仓储发货）。角色归属一律走 `admin_user_role` 关联表，`admin` 表里不存角色字符串。
- **问题**：RBAC 建表时把角色名误填进了 `admin.name`，4 个演示账号的 name 是「管理员 / 运营 / 客服 / 仓储发货」，与各自角色的 `role_name` 一字不差。前端账号页同时渲染「姓名」列和「角色标签」，看到的都是同一个词；同岗位再加一个人就彻底分不出来，将来操作日志审计记录的操作人只能是岗位名不是人。
- **修法（纯数据修正 + 注释澄清，不动表结构、不动逻辑）**：本地库已 UPDATE（admin→王芳、operator→李强、service→赵敏、warehouse→孙磊）；`admin.name` 注释由「显示名」改为「管理员姓名」；前端列头/表单 label「显示名」→「姓名」；后端校验提示「显示名不能为空」→「姓名不能为空」；两处种子 SQL（`docs/sql/admin.sql`、`docs/sql/rbac/01-rbac-schema.sql`）同步改值，避免新库导入时复发。
- **生产**：`docs/sql/rbac/03-fix-admin-name.sql`（按 username 定位、幂等、含改前快照与回滚语句），**本地与生产均已执行并核对**。
- **安全性核对**：`admin.name` 无唯一索引（`uk_admin_username` 只约束 username），不参与任何查询条件、也不参与权限判定（权限走 `admin_user_role → admin_role.role_key`），所以改它不影响登录和 RBAC；`admin.name` 在代码里只有两处用途：登录后右上角显示、账号列表 VO。

### 沙箱编号段主键压平（2026-11-01）
- **背景**：早期为隔离沙箱，各表主键按段位手工指定（订单 51000220、用户 29000022、售后 10009115……）。沙箱下线后号段没回收，新数据 id 继续跳号（本地 orders 下个 id 51000221、生产 249000290），既难看又容易撞号。
- **处理原则（脚本 `docs/sql/normalize-sandbox-ids.sql`）**：
  1. **只动 id >= 100000 的行** —— 本来就正常的 id 一行不改，老订单链接、老用户 id、老商品 id 全部保持可用；
  2. 新 id = 该表「现有正常 id 最大值 + 按老 id 升序的排名」，保证新老 id 单调递增、与创建顺序基本一致；
  3. 引用列用映射表 JOIN 批量改，**不依赖执行顺序**（映射表是 old→new 快照，主键改完后引用列仍能匹配）；
  4. 映射靠相关子查询算排名，**不写死行数/id**，所以同一份脚本本地生产各跑各的都对；
  5. 幂等：重复执行不报错、无残留。
- **本次实际改动**：本地 90 行、生产 121 行；覆盖 user/admin/tags/address/cart/orders/order_items/after_sales 及 3 张售后子表。products、categories、stock_alert、admin_menu 等 id 本来就正常，未动。
- **已级联修正的引用列**（别漏）：orders.user_id、order_items.order_id、after_sales.order_id、after_sale_items.after_sale_id/order_item_id、after_sale_only_refunds/returns/exchange_returns.after_sale_id、cart.user_id、address.user_id、feedback.user_id、product_favorites.user_id、product_reviews.order_id/user_id、promotion_usage.order_id/user_id、user_coupon.order_id/user_id、admin_user_role.admin_id、products.tag_id。
- **注意**：`product_reviews.order_id` **没有外键约束**，只能靠脚本手工同步 —— 以后加引用列时别只看 FK 列表。
- **AUTO_INCREMENT 计数器**：MySQL 不允许把计数器调到 <= 当前 max(id)，但设成 max(id)+1 是允许的，所以脚本用 `ALTER TABLE ... AUTO_INCREMENT = max+1` 归位（本地 8.0 / 生产 5.7 都验证过生效）。**本地 MySQL 8.0 的 information_schema.TABLES 有统计缓存（默认 1 天）**，查 AUTO_INCREMENT 前要 `SET SESSION information_schema_stats_expiry = 0`，否则看到的是执行前的旧值（5.7 无缓存，直接查即实时）。
- **副作用与处理**：
  - C 端 token 带 user id，老 token 不报 401 只是查不到数据（小程序不会自动重登）→ 实测受影响面很小：生产有订单的 8 个用户各 1 单、2 个购物车、8 个地址，主力用户（id=13 有 246 单、id=5/6/7）本来就是正常 id 完全没动；
  - 后台 4 个账号 id 变了 → 已用 Redis `auth:admin:invalid-before:{旧id}` 写失效标记（TTL 604860s），旧 token 立即失效、自动重新登录；
  - `after_sale_items.order_item_id=74` 的孤儿是**历史遗留**（order_items 压根没有 id=74 这个行），与本次无关；核对孤儿时注意 `tag_id` 为 NULL 不算孤儿。
- **备份**：本地 `backup_before_normalize_ids_2026-11-01.sql`；生产 `/www/backup/e_commerce_db_2026-11-01_0942.sql`。

### 商品价格校正（2026-11-01）
- **问题**：products 的价格是造数据时按「cost_price × 1.16」编的，而 cost_price 本身也是编的，导致小件品类价格离谱：手机壳 ¥97~373、米家台灯 ¥900、智能灯泡 ¥829、门磁传感器 ¥2515、人体传感器 ¥1546、读卡器 ¥803、U 盘 ¥1543；大件里也有明显偏离真实市场价的（索尼 A6700 ¥25360、索尼 A7 IV ¥24976、石头 G20 ¥1348、AirPods 4 ¥499）。
- **处理原则**：按各品类真实市场价格重新定价，售价取整；划线价 = 售价 × 1.10；成本价 = 售价 × 0.72（零售毛利率 28%）。未调价的 38 个商品也一并统一划线价/成本价口径 —— 旧数据里存在「成本价 > 售价」的倒挂（如 iPhone 15 Pro 成本 ¥9699 > 售价 ¥8499）。
- **脚本**：`docs/sql/fix-product-pricing.sql`
  1. **按「商品名 + 原售价」匹配，不按 id** —— 商品若已被人工改过价就自动跳过，不会覆盖别人的修改；天然幂等（第二次执行旧价已不存在，匹配 0 行）；
  2. 映射表 98 条，含改前价格快照 SELECT、改后自检 SELECT（成本 < 售价 < 划线价，异常 0 行）；
  3. 只读预览版：`docs/sql/fix-product-pricing-preview.sql`（生产改价前必须先跑这个出对照）。
- **实际改动**：98 个商品调价；手机配件区间 ¥97~373 → ¥39~99；智能照明 ¥277~929 → ¥49~299；智能安防 ¥284~2815 → ¥39~399；存储设备 → ¥79~599；充电设备 → ¥69~179；电脑配件 → ¥99~399；扫地机器人统一 ¥2599~3299。**本地与生产均已执行并核对一致**。
- **影响面核对**：只改 products 的 sales_price/market_price/cost_price；历史订单价格记在 order_items.price（快照）不受影响；promotion / promotion_rule / promotion_item / promotion_usage 四张促销表**当前全为空**，不存在价格区间错配；商品详情 body 里不含价格文字（详情是图片）；Redis 只缓存分类树（30 分钟）和推荐结果（60 秒），**不缓存商品价格**，无需清缓存。

### 后台来单提醒 WebSocket 修复（2026-11-01）
- **现象**：后台 `wss://www.jiemids.com/ws/order?token=...` 一直连不上（DevTools 报 WebSocket failure），来单提醒/语音播报失效。
- **分层定位**（关键：先分清是后端还是链路）：
  | 测试 | 结果 |
  |---|---|
  | 网关直连 `http://127.0.0.1:8080/ws/order` + 真实 admin token | **101 Switching Protocols** ✓ |
  | 公网 `https://www.jiemids.com/ws/order` + 同一个 token | **HTTP/2 400** ✗ |
  | 源站 access log | Chrome 的请求也是 400、响应体 34 字节（Spring 的错误体，不是 nginx 的 400 页） |
  → 后端、网关、token、nginx `/ws/` location 全部正常，问题在 CDN 回源这一段。
- **根因**：nginx 的 `/ws/` location 里写的是 `proxy_set_header Upgrade $http_upgrade;`，而**腾讯云 EdgeOne 回源时把客户端的 `Upgrade` 头剥掉了**（hop-by-hop 头，EdgeOne 没开 WebSocket 加速就按普通代理处理）。实测探针（在 extension 目录临时加一个 `location = /ws-probe`，把 `$http_upgrade`/`$http_sec_websocket_key`/`$http_sec_websocket_version`/`$http_connection` 用 `add_header` 打出来）显示：`Sec-WebSocket-Key`、`Sec-WebSocket-Version`、`Connection: Upgrade` 都还在，**只有 `Upgrade` 是空的** → nginx 转发给上游时 `Upgrade` 为空 → Spring 判定握手不完整 → **400 Bad Request**。
- **修法**：把 `Upgrade` 写死 —— `proxy_set_header Upgrade websocket;`（这个 location 只服务 WebSocket，写死安全；客户端的 Upgrade 值本来就恒为 `websocket`）。改完源站日志从 400 变成 **101**（成功升级）→ 浏览器请求变成 **499**（长连接建立后客户端断开，nginx 对长连接的正常记法）。
- **配置放 extension 目录**：`location ^~ /ws/` 已从宝塔托管的 `html_jiemids.com.conf` **移出**，改放 `extension/jiemids.com/ws.conf` —— 主 conf 被宝塔保存站点配置时重写（`xiaojiecpn.com` 就因此掉过 443 SSL 块，见上文线上事故），扩展目录不会被冲掉。注意主 conf 与 extension 里**不能同时存在 `location ^~ /ws/`**，会触发 `duplicate location` 导致 `nginx -t` 失败。
- **备份**：`/www/server/panel/vhost/nginx/html_jiemids.com.conf.bak-2026-11-01-125212`（及迁移时又备份一份）。
- **排查手法（可复用）**：① 先用「网关直连 + 手工签一个 token」区分是后端还是链路；② 用 `add_header` 探针把关键请求头打出来，看 CDN 到底传了什么；③ 看源站 access log 的状态码：**400 = 握手残缺、101 = 升级成功、499 = 已连接后被关闭**。
- **遗留**：EdgeOne 控制台建议给 `www.jiemids.com` 开启 **WebSocket 加速**（当前未开，边缘不转发 Upgrade，靠 nginx 写死兜底）。若不开，边缘与浏览器之间的长连接可能不稳，表现为连上又断 —— 前端有 25 秒心跳 + 退避重连兜底。

### 本地 ↔ 生产库一致性审计（2026-11-01）
- **背景**：「每次改本地 MySQL 都要同步生产」这条规矩执行下来一直缺一个**核对**环节 —— 本次逐表比对才发现两边的差异，同时暴露了一类**方向相反**的差异（本地改了没同步、生产改了没同步）。
- **比对方法（可复用）**：
  1. **字段**：比 `information_schema.COLUMNS`（表名/字段名/类型/可空/默认值）。⚠️ 本地 MySQL 8.0、服务器 5.7，`bigint` vs `bigint(20)`、`int(11)`、`tinyint(4)` 是**展示宽度差异**，必须先用正则归一化再比，否则会误报几百条。
  2. **索引**：比 `information_schema.STATISTICS` 的 (表, 索引名, 非唯一, 列, 序号)。⚠️ 两边排序规则不同（`utf8mb4_0900_ai_ci` vs `utf8mb4_general_ci`），`ORDER BY` 出来的行序会不同，**要比集合不能比序列**。
  3. **数据**：对配置类表算 `MD5(GROUP_CONCAT(CONCAT_WS('|',列...) ORDER BY 主键))` 指纹，两边逐行比；`GROUP_CONCAT` 默认长度上限 1024，先 `SET SESSION group_concat_max_len = 1048576`。
  4. 工具：`docs/sql/check-local-prod-parity.sql`（含上面 10 张配置表的指纹），两边各跑一遍比对。
- **本次发现的三类差异**：
  | # | 差异 | 归属 | 处理 |
  |---|---|---|---|
  | 1 | 生产缺 `user.uk_user_username` 唯一索引（`docs/sql/user-pc-auth.sql` 头部却写着「本地与生产均已执行」） | **本地→生产漏同步** | 生产补建（`sync-local-to-prod.sql`，幂等） |
  | 2 | `categories.recommend`：生产 {101,201,301,302,501,601,801}、本地还是种子集合 {101,102,201,301,501,601} | **各自在后台控制台改过**（种子脚本注释还写着「与本地一致」，实际生产被人手改过） | **以生产为准**同步本地 |
  | 3 | `tags`：生产只有「最新」（绑 10 件），本地多一个 2026-10-29 在本地后台点出来的演示标签「爆款」（绑 17/42、并解绑 10/11、绑上 4）；生产多一组已禁用的「客服2」账号+角色；`admin.id=1` 手机号生产为空、本地是种子值 | 同上 | **以生产为准**同步本地 |
- **处理原则（重要）**：
  - **结构缺陷 → 以本地为准，往生产补**。唯一索引是代码依赖（PC 商城用户名全局唯一，同名注册出现两条记录会让登录 `selectOne` 抛 TooManyResultsException），生产缺就是生产有 bug。
  - **运营配置 → 以生产为准，往本地对齐**。首页推荐分类、商品标签绑定是**线上正在展示给真实用户**的东西，为「对齐」去改生产等于拿线上体验冒险；反过来把生产的人工调整同步到本地，零风险。本地是开发沙箱，生产才是业务事实。
  - 判据：**生产是人实际操作的地方（用户会登录后台点），本地是脚本操作的地方** —— 谁是人改的，就以谁为准。
- **脚本**：
  - `docs/sql/sync-local-to-prod.sql` → 执行到**生产**：幂等补 `uk_user_username`（先查 `information_schema.STATISTICS` 再建，重跑只提示跳过），带三条校验 SELECT。
  - `docs/sql/sync-prod-to-local.sql` → 执行到**本地**：recommend / tags / 商品标签绑定 / 角色账号对齐，幂等可重复。
  - `docs/sql/gen-admin5-transfer.sql` → 搬运生产 admin 第 5 行（密码是 BCrypt 哈希）：用管道把生产的 INSERT 文本直接喂给本地 mysql，**哈希不落盘、不进对话记录**。
- **核对结果**：索引 102/102 一致；10 项配置指纹全同；生产备份 `/www/backup/e_commerce_db_2026-11-01_1330.sql`。
- **顺带确认线上代码是否已发**：容器镜像 tag 即为发布记录 —— `auth-service:20261101-c7b4851`（含「姓名不能为空」）、其余服务 `20261031-9e60424`（含 PC 用户名登录、增值保障规则）、后台前端 dist 11:02、PC 商城 dist 10-31 22:40；比对 `git merge-base --is-ancestor <功能提交> <镜像对应提交>` 即可确认某功能是否已上线。

### 后台菜单管理 + 令牌失效（2026-10-30）
- 菜单管理：`GET /api/admin/system/menus/manage-tree`（含停用/隐藏/按钮）+ `POST/PUT/DELETE /api/admin/system/menus`，权限 `system:menu:list/add/edit/remove`；角色分配继续用 `GET /menus/tree`（仅 status=1，权限 `system:role:assign`），两个 VO 分开，RoleList 不受影响。
- 菜单校验：类型 M/C/F；父级必须存在且不能是 F；F 必须挂在 C 下且不能挂根；编辑时向上遍历父链检测环；C 必须有 `/` 开头的 path；perms 非空时全局唯一（应用层，无 DB 唯一索引）；有子级不能改类型；有子级或被 `admin_role_menu` 引用不能删除。
- 令牌失效：不新增数据库列，Redis 存 `auth:admin:invalid-before:{adminId}`（epoch 秒，TTL = `jwt.expire + 60`）；common `AdminTokenRevocationStore` 负责读写，`JwtInterceptor` 对 admin token 比较 `iat < 失效时间点` 即返回 401。账号角色/状态/重置密码、角色状态/菜单分配、菜单 perms/status 变化时，在**事务提交后**写入失效标记（`TransactionSynchronization.afterCommit`）；超管（`role_key=admin`，权限固定 `*:*:*`）不参与角色/菜单的批量失效。只改姓名/手机号/菜单名称排序图标不会踢人。Redis 异常 fail-open，warn 日志每分钟最多一条。
- 部署注意：`JwtInterceptor` 在 common，所有依赖 common 的服务都要重新发布；`user/aftersale/file` 补了 `spring.data.redis` 配置，auth/product/order 补 `timeout: 2s`。
- 前端守卫：token 缺 `permissions`/不是 admin/解析失败 → 清 token 回登录页；缺路由权限时跳第一个有权限的静态路由，找不到再清 token，彻底避免 `/workplace` 自跳死循环。
- 后台表格列宽：**不要给所有列都写 `width`**。Ant Design Vue 会给固定宽度列生成 colgroup，总宽小于容器时表格右侧会留白（菜单管理曾在 1920 下空出 337px，和右上角按钮对不齐）；保留少量窄列固定宽度，文字列留空自动分配。给名称列写 `width: 1px` 想按内容收缩也不行——colgroup 会按 1px 硬约束，把展开图标和名称挤成两行。
- 菜单管理树表 UI（2026-10-30 定稿）：默认收起（保留用户手动展开的层级，新增子项后展开父级）；图标用 stepin 全局注册的 Ant 图标组件 `<component :is="record.icon">` 渲染，不单独占列，放在名称左侧；名称列 `align:left` + 固定 200px，让 `+`、图标、名称紧挨；「类型」保持独立列固定 90px；路由路径/权限标识自适应填满剩余宽度。
- 树表展开图标：Ant Design 会把 `children: []` 当成可展开节点，叶子也会显示 `+`；前端 `normalizeTree` 递归删除空 `children` 后，只有真实父级显示 `+`（叶子保留隐藏占位，保证名称缩进对齐）。
- 操作列：条件按钮（删除/禁用）缺失时用固定 3 列 grid + 空占位补齐，避免每行按钮个数不同导致居中错位；按钮统一 `type="link"`，危险操作用 `danger`。
- 管理员账号页：状态列改 `a-tag`，操作列新增「禁用/启用」+ `a-popconfirm` 二次确认（禁用会立即让对方 token 失效）；超管 id=1 和当前登录账号不显示禁用按钮。
- 已知限制：`iat` 秒级精度，变更与签发同秒最多延迟 1 秒失效；WebSocket 握手后不因失效主动断开；父菜单停用不级联子菜单权限（旧行为）。

### 后台登录改造（2026-10-30）
- 移除后台手机号验证码登录：删除 auth-service `/api/auth/sms-code`、`/api/auth/phone-login`、`SmsCodeVO`/`PhoneLoginDTO`/`SendSmsCodeDTO`、SMS 配置以及 `AdminMapper.findByPhone`。
- 新增图形验证码：`GET /api/auth/captcha` 返回 `captchaId` + base64 PNG；答案存 Redis `auth:captcha:{id}`，180 秒，校验后立即删除（一次性）；`POST /api/login` 必须带 `captchaId/captchaCode`。
- 网关 auth-service 路由、`WebMvcConfig` 白名单同步改为 `/api/auth/captcha`；后台登录页移除手机号入口，改为账号密码 + 图形验证码，登录失败自动刷新验证码。
- 登录失败锁定（2026-10-30）：`auth.login.max-fail-count=5`、`auth.login.lock-minutes=10`（yml / 环境变量，不建参数表）；Redis key `auth:login:fail:{username小写}:{ip}`，密码错误计数，达到 5 次返回 429「尝试次数过多，请 X 分钟后再试」，成功登录清零；**验证码错误不计数**，按「用户名 + IP」避免恶意锁死账号；IP 优先取 `CF-Connecting-IP`，其次 `X-Forwarded-For`/`X-Real-IP`。
- 仍未处理：登录页生产环境预填 `admin/admin123`、`admin.password` 明文存储/比较。
- 注意：生产发布顺序为 **auth-service + gateway + 后台 dist**；captcha 和失败锁定都依赖 Redis。

### 库存预警表改名（2026-10-30）
- `stock` 表只存库存预警配置（`min_stock/max_stock/alert_enabled`），当前库存仍在 `products.stock`；原名容易被误解为库存数量表，已改名为 **`stock_alert`**，唯一索引 `uk_stock_alert_product`、外键 `fk_stock_alert_product` 同步改名。
- 脚本 `docs/sql/stock/01-rename-stock-alert.sql`（幂等）；本地与生产均已执行，生产备份 `/www/backup/e_commerce_db_before_stock_alert_2026-10-30_1020.sql`。
- 代码：`Stock` → `StockAlert`、`StockMapper` → `StockAlertMapper`、XML 同步改名；后台 API `/api/admin/stock` 与前端字段不变。
- 种子文件 `stock-data.sql` 原来 30 行全部 `product_id=1`（错误），已按实际库重新导出为 `docs/sql/stock-alert-data.sql`（30 个不同商品）；`03a-remove-old-seed.sql` 与生成器中的 `DELETE FROM stock` 同步改为 `stock_alert`。

### 会员模块（2026-10-30）
- 简化版：**成长值等级 + 等级标识 + 会员专享券**，不做积分/付费 VIP/会员价/生日券/浏览记录，不引入 MongoDB。
- 表：`member_level`（4 档：0/500/2000/5000，可后台改）；`user` 加 `member_level_id/growth_value/total_paid_amount/paid_order_count`；`coupon_template` 加 `min_member_level`（0=不限）。脚本 `docs/sql/member/01-member-schema.sql`，本地与生产均已执行（生产备份 `/www/backup/e_commerce_db_before_member_2026-10-30_0951.sql`）。
- 成长值 = 已支付订单 `pay_amount` − 已退款 `after_sale_items.refund_amount`（仅 `status=refunded`），1 元 = 1 成长值；`MemberServiceImpl.getProfile` 在用户打开会员中心/我的页时重算并回写，等级只升不降，无定时任务、无流水表。
- 接口：C 端 `GET /api/member/profile`、`/api/member/levels`；后台 `GET /api/admin/members`、`GET/PUT /api/admin/member-levels`（user-service，网关 `/api/**` 已覆盖）。
- 券：领券中心、领取、确认页可用券、下单核销都校验 `min_member_level`，等级不足不展示/不可领；后台优惠券页增加「会员门槛」。
- 前端：后台「经营工具 → 会员」含会员列表 + 等级配置；小程序「我的」会员卡显示等级/成长值，新增 `pages/my/member` 会员中心，领券中心展示「会员专享」标签；`features.vip` 已置 true。

### PC 商城 Web 端（ecommerce-mall，2026-10-29）
- 独立前端 `D:\study\my-workspace\wechat-workspace\ecommerce-mall`：Vue 3 + Vite + Pinia + Router + Axios + Element Plus，SPA；替换原静态企业站。
- 线上 https://www.xiaojiecpn.com ，站点根 `/www/wwwroot/xiaojiecpn.com`；Nginx 配置 `extension/xiaojiecpn.com/mall.conf`（源文件 `ecommerce-mall/deploy/xiaojiecpn-mall.conf`）：`/api`、`/image`、`/upload` 同源代理到 `127.0.0.1:8080` + `try_files $uri $uri/ /index.html`。**443 站点块在主配置 `html_xiaojiecpn.com.conf` 末尾**（2026-10-31 补，EdgeOne 回源走 HTTPS，详见「线上事故：PC 商城首页 404」）。
- 已上线：首页（分类 + 猜你喜欢）、商品列表/分类、商品详情、搜索，均接真实 C 端接口；游客可浏览。
- 发布：**在 e-commerce-boot 里执行 `./deploy/release-mall.sh`**（2026-10-31 新增的专用脚本，写死 `WEB_DIR=../ecommerce-mall` + `WEB_ROOT=/www/wwwroot/xiaojiecpn.com`，自动留 `.bak.<时间戳>` 备份）。⚠️ **不要用裸的 `./deploy/release-web.sh` 发商城** —— 它默认发后台管理端到 `/www/wwwroot/jiemids.com`，2026-10-31 已因此误发过一次。
- 约定：单店 B2C、不做商家入驻、不做 SKU；登录后置，首页/浏览不登录，加购/购买再登录。
- **Web 登录（2026-10-31 更新：支持用户名 + 手机号双账号）**：不复用后台 admin 登录，user-service 新增 `POST /api/user/login|register|send-code|login-code`。
  - **账号密码登录**：`POST /api/user/login` 入参 `username` + `password`，**只认用户名**。刻意不做「用户名或手机号二选一」的模糊匹配 —— 两种登录方式在 PC 端是两个独立 tab，凭据各归各，混用会让「我明明用手机号登的怎么提示用户名错误」这类困惑无法排查。手机号登录一律走验证码那条路。密码 BCrypt；用户名不存在与密码错误返回**同一句**「用户名或密码错误」，不暴露账号是否注册。
  - **手机号验证码登录**：`POST /api/user/login-code`（手机号 + 6 位码，Redis 5 分钟、60 秒限频；暂不接短信，接口直接返回模拟码；未注册自动建号）。
  - **注册**：`POST /api/user/register` 入参 `username` + `phone` + `password`（`PcRegisterDTO`）。用户名规则 `^[a-zA-Z0-9_]{3,30}$`（与后台 admin 一致），**统一转小写存储**；表 collation `utf8mb4_general_ci` 大小写不敏感，DB 唯一索引 `uk_user_username` 拦截重名。
  - `user` 表新增三样：`username` VARCHAR(64) NULL + `uk_user_username` 唯一索引、`password` VARCHAR(255) NULL（BCrypt）、`idx_user_phone` 普通索引。`phone` 历史数据可能重复，**不建唯一索引**，注册查重走服务层。迁移脚本 `docs/sql/user-pc-auth.sql`（**已在本地与生产执行**）。
  - PC 账号 `openid` 是 NOT NULL + 唯一键，用 `pc_<uuid>` 占位；注册时 `name` 用 username 兜底，避免后台会员列表空昵称。
  - **踩坑**：`loginByCode` 原用 `opsForValue().getAndDelete()` → 那是 **Redis 6.2+ 的 GETDEL**，本机旧版 Redis 报 `unknown command`，本地验证码登录一直失败（生产是 `redis:7-alpine` 没暴露）。已改 **Lua 脚本**（`GET`+`DEL` 原子），兼容所有版本。
  - PC 端全局 `LoginModal`（登录/注册 tab，登录内密码/验证码切换）；详情页加购/立即购买、购物车入口未登录时先弹框，登录成功自动继续原操作；401 改为派发 `auth-expired` 事件弹框，不再跳不存在的 `/login`。
- 文档：`ecommerce-mall/README.md`（决策、P0-P5 checklist、发布步骤）。

### WebSocket 来单提醒
- `order-service /ws/order`，握手 query `?token=` 只校验 admin 类型；单店模式用单 `Set<WebSocketSession>` 注册表，支付成功 `broadcast` `{type,orderId,content}`。
- 前端登录后自动连，弹窗 + 语音 + 25s 心跳 + 断线重连；多实例广播计划用 RabbitMQ fanout（见下）。

### AI 服务（Python `recommender-python`）
- FastAPI + LangGraph，接口 `/api/agent/chat`、`/api/agent/chat/stream`、`/health`；JWT 只校验不签发，每用户限流。
- 生产容器 `ai-service:8000`，网关 `/api/agent/**` 转发；容器连宿主机 MySQL 需 `MYSQL_HOST=host.docker.internal`（2026-10-29 补上）。
- 商品查询只读 `products + categories`，条件 `listing_status=1 AND stock>0`（无 shop_id）。

### RabbitMQ（2026-10-29 起）
- 本地 Docker：`docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3.13-management`；管理台 http://127.0.0.1:15672 ，账号 `guest` / 密码 `guest`。
- `order-service` 已加 `spring-boot-starter-amqp` + `spring.rabbitmq.*`（环境变量 `RABBITMQ_*`，默认本机 guest/guest）。
- dev-only demo：`ecommerce.order.mq.demo`，启动自动发一条并消费；生产 profile 不加载。
- 待做：未支付 30 分钟延迟队列、WebSocket 跨实例 fanout、生产 compose 加 rabbitmq。

### 增值保障规则（2026-10-31）
- 起因：用户反馈「鼠标、手机壳这种便宜商品没必要加增值保障」。排查发现 **products 表根本没有保障相关字段**（22 列里没有），保障卡片是写死在前端的，三端一视同仁 —— 97 元的手机壳也弹「电池无忧 ¥20」「全面保修 ¥0.97」。
- 规则收口到 **common**：`ecommerce/common/insurance/InsurancePolicy`（唯一规则处，product-service 与 order-service 共用）+ `InsuranceOptionVO`。口径：
  - 售价 < 300 元不出保障；
  - 数码配件类目不出保障：701 手机配件 / 702 电脑配件 / 703 存储设备 / 704 充电设备（壳膜、扩展坞、充电器本身就有品牌质保）；
  - 「电池无忧」只对有电池的类目开放，排除 401/402/403 耳机音箱、801 智能音箱、803 智能照明；
  - 费用：全面保修 = 售价 1%、一年意外保 = 售价 2%、电池无忧 = 固定 20 元。
- 接口：`GET /api/products/{id}` 返回 `insuranceOptions: [{code,name,fee,desc}]`（不适用时为空数组），按**最终展示价（含活动价）**计价；列表接口不返回该字段。VO 用 common 的 `InsuranceOptionVO`；后台 `AdminProductVO` 也带同字段。
- 前端只渲染：`insuranceOptions` 为空则整块隐藏。已改：小程序 `pages/goods/detail.vue`、PC 商城 `src/views/products/ProductDetail.vue`。**后台商品预览 `e-commerce/src/pages/goods/list/Mall.vue` 保持旧硬编码**（用户 2026-10-31 决定先只管 C 端），所以后台预览里配件仍会显示三张卡。
- 保障明细字段统一成 `{code, name, fee}`（PC 商城原先用 `{name, price}`，与小程序不一致）。
- **order-service 一并收紧**（用户 2026-10-31 决定）：加购/下单不再采信前端传来的 `insuranceDetail`/`insuranceFee`，改由 `order-service/support/InsuranceReviewer` 按 `InsurancePolicy` 复核 —— 只保留该商品允许的保障项、费用按活动价重算、明细由后端重新序列化。order-service 与 product-service 同库，`order/entity/Product` 加了 `categoryId`（`ProductMapper.xml` 的 findById 补 `category_id`），加购时用 `promotionPricingService.quoteDirect` 取活动价计价。
- 验证（本地）：
  - product-service 全量 136 个上架商品：81 个 3 项、29 个 2 项（无电池）、24 个配件类排除、2 个低价排除（284 元摄像头、277 元灯带），与规则完全一致。
  - order-service 加购实测：iPhone 前端传 fee=999 + 伪造 code → 后端重算为 274.97 并剔除伪造项；AirPods 传「电池无忧」被剔除；手机壳、鼠标传任何保障都被剔除成 0.00 / `[]`。
  - order-service 下单实测：手机壳伪造 fee=999 → 订单 insurance_fee=0.00、insurance_detail=`[]`、pay_amount=97.31。
- 未做：`order-service` 购物车里已存在的旧快照（加购时是旧规则下的数据）不会回溯重算。
- **生产库核对（2026-10-31，SSH 直连生产查的）**：本次改动 **零 SQL**，生产库不需要同步任何东西。已核对：① `products` 表 22 列与本地逐列一致（含 `category_id`，order-service 新 SQL 依赖它）；② `order_items`/`cart` 的 `insurance_detail varchar(512)`、`insurance_fee decimal(10,2)` 两边一致；③ 四张表 collation 都是 `utf8mb4_general_ci`；④ `categories` 全表 md5 两边相同（`a57bc997...`）；⑤ `products` 逐列 md5：name/sub_title/brand/title/sales_price/market_price/category_id/listing_status/specs/product_code/image 全一致，只有 **stock 不同**（线上有真实订单扣库存，属正常）；⑥ 生产 136 个商品全上架、无空字段、无 null category_id，按新规则会有 26 件不出保障 —— 与本地完全一致。⑦ 生产当前仍是旧代码（镜像 tag `20261030-aacd85f-dirty`），接口尚无 `insuranceOptions` 字段。
- `products.id=128` 的图（**本地/线上都正常，无需任何改动**）：本地库 image = `/image/upload/e8dc2234-9fe6-4dc4-a3fe-20854f992049.jpg`，本地 `upload/image/upload/` 下该文件存在（89,293 字节，2026-10-31 09:00）；生产库 image = `/image/upload/128.webp`，生产服务器上 `128.webp`（6,786 字节）与同名 `e8dc2234-….jpg`（89,293 字节）**两个文件都在**。两者是同一张浪琴名匠 L2.628 男表图的不同压缩版本（128.webp 是带「浪琴 / 名匠 L2.628 机械男表 / SEED-128」字样的宣传图，UUID jpg 是实物白盘图）。两边各自的库里指向的文件都存在、都能正常显示。
- 生产 `order_items` 里有 8 条历史订单 `insurance_fee>0`（老规则下真实成交），金额已定、不受影响；生产 `cart` 里 0 条带保障。

## 发布与部署

- TCR：`ccr.ccs.tencentyun.com`，命名空间 `jiemi-ecommerce`，**8 个仓库**（7 Java + ai-service）。
- 服务器 `/www/java`，SSH 别名 `ecommerce`（root@193.112.176.228，私钥 `~/.ssh/ecommerce.pem`）。
- 发布：`./deploy/release.sh <svc>`（Maven → docker build/push → SSH pull + `compose up -d --no-build --no-deps`）；`--no-build`、`--no-deploy`、`--dry-run`、`--rollback <tag>`。
- **发布顺序**：product → order → aftersale → user → ai → 后台前端 → gateway → **auth 最后**（auth 停止签发旧 claim 后旧服务会拒绝新 token）；`ALL_SERVICES` 已把 auth 排最后。
- 后台前端：`./deploy/release-web.sh`（npm build → tar 上传 → rsync 到 `/www/wwwroot/jiemids.com`，保留 `.env/.htaccess/.user.ini/.well-known/public`，自动留 3 份备份）。
- **两个前端站点必须用各自的脚本**（2026-10-31 新增 `release-mall.sh` 后固定）：

  | 站点 | 域名 | 站点目录 | 前端工程 | 发布命令 |
  |---|---|---|---|---|
  | 后台管理端 | www.jiemids.com | `/www/wwwroot/jiemids.com` | `../e-commerce` | `./deploy/release-web.sh` |
  | PC 商城 | www.xiaojiecpn.com | `/www/wwwroot/xiaojiecpn.com` | `../ecommerce-mall` | `./deploy/release-mall.sh` |

  两个脚本底层都是 `release-web.sh`，靠 `WEB_DIR`/`WEB_ROOT` 区分；**漏带环境变量就会发错站点**（2026-10-31 已踩过一次，误把后台端重发到 jiemids.com）。`release-mall.sh` 已把两个值写死并加了工程目录校验。
- compose 每个服务同时有 `image:` 和 `build:`；服务器必须 `--no-build`。`docker-compose.override.yml` 由 release.sh 生成，固定各服务当前镜像，全量 `up -d` 不要带新 TAG。
- 服务器 `.env` 已有 `REGISTRY`；2026-10-29 补了 `MYSQL_HOST=host.docker.internal` / `MYSQL_PORT` / `MYSQL_DATABASE` 给 AI。
- `upload` 是宿主机目录 `/www/java/upload`，bind mount 到 product/user，需单独备份。
- 单店化备份：`/www/backup/e_commerce_db_before_single_shop_*.sql`（含 `Dump completed` 校验）。

### 线上事故：PC 商城首页 404（2026-10-31）

- **现象**：浏览器访问 `https://www.xiaojiecpn.com/` 显示 nginx 的 404 页面，但 DevTools 状态码是 **200 OK**（该 404 页面是静态文件，状态码 200）。
- **链路**：`浏览器 → 腾讯云 EdgeOne（DNS 113.108.215.156 / 119.147.114.212 / 14.29.51.193）→ 源站 nginx`。EdgeOne **回源走 HTTPS(443)**，而源站当时**没有 www.xiaojiecpn.com 的 443 站点块**（`html_xiaojiecpn.com.conf` 只有 `listen 80`），请求落到 `server_name _` 的 default 站点，返回 138 字节的 nginx 404 页面（`etag 6a95b4ca-8a`，与 EdgeOne 返回的完全一致 → 确认是同一个源站文件）。
- **诱因**：`html_xiaojiecpn.com.conf` 的 mtime 是当天 **10:14:23** —— 宝塔保存站点配置时重写了该文件，**把 443 SSL 站点块冲掉了**（证书文件本身还在：`vhost/cert/xiaojiecpn.com/{fullchain,privkey}.pem`，CN=www.xiaojiecpn.com，SAN 含裸域，有效期 2026-08-31 ~ 2026-11-29，只是没有任何 server 块引用它）。
- **修复**：在 `html_xiaojiecpn.com.conf` **末尾追加**一个 443 server 块（复用现成证书 + `include extension/xiaojiecpn.com/*.conf` 提供 /api、/image、/upload 反代与 SPA 伪静态），`nginx -t` 通过后 reload。备份 `html_xiaojiecpn.com.conf.bak-20261031-114239`。
- **注意**：
  - 80 端口保留（EdgeOne 强制 HTTPS、ACME 验证、应急直连），用户侧全程 HTTPS，**EdgeOne→源站也是 TLS**（没有走明文回源）。
  - 该文件由**宝塔托管**，宝塔保存站点配置会重写它 → 必须去宝塔把该站点 **SSL 开关打开**，否则下次保存又掉 443。
  - 证书 **2026-11-29 到期**，需在宝塔设自动续签或换证。
  - 修完后 `/`、`/index.html`、各路由、`/assets/*`、`/api/*` 回源全部正常；但 EdgeOne 边缘仍缓存着旧的 404（`EO-Cache-Status: HIT`，Age 3100s+）。**未做手工刷新**：TTL 到期后边缘自动回源，11:56 复查三个 EdgeOne IP 与本地代理路径全部返回正确的商城首页（1022 字节），无需去控制台刷缓存；若是紧急故障再手工刷（URL 刷新 `https://www.xiaojiecpn.com/`）。

### 线上事故：后台子路由 404（2026-10-31，同日）

- **现象**：`https://www.jiemids.com/goods/list`、`/login`、`/system/users` 全部 nginx 404（首页正常）。
- **根因**：后台是 Vue3 **history 路由**，但 `html_jiemids.com.conf` 里只有 `/api/`、`/image/`、`/upload/`、`/ws/` 四个反代，**没有 `location /` 的 SPA 回落**；`rewrite/html_jiemids.com.conf` 也是宝塔默认空文件 → 任何子路由直接 404（刷新即白屏）。
- **修复**：新建 `/www/server/panel/vhost/nginx/extension/jiemids.com/spa.conf`：
  ```
  location / { try_files $uri $uri/ /index.html; }
  ```
  放 extension 目录（主配置里 `include extension/jiemids.com/*.conf`），宝塔重写主 conf 时不会丢。reload 后 `/`、`/goods/list`、`/login`、`/system/users` 全部 200 返回后台首页 HTML。
- **同源模式提醒**：PC 商城（`extension/xiaojiecpn.com/mall.conf`）早就有 `location / { try_files ... /index.html; }`，所以商城子路由一直正常 —— 两个站点的配置并不一致。

### 线上巡检

- `deploy/healthcheck.sh`：检查商城首页 / 商城接口 / 商城静态资源 / 后台 / 小程序两个接口。
- **商城首页故意不做 cache-buster**（请求的就是用户实际访问的 URL）——2026-10-31 那次"源站好、线上坏"的故障只有按用户视角检查才测得出来；接口类才加时间戳绕过缓存。
- 告警走企业微信/钉钉机器人（`healthcheck.env` 里的 `HEALTHCHECK_WEBHOOK`），未配置则只写 `/www/logs/healthcheck.log`；连续失败 2 次才告警。

## 已知风险 / 遗留

| 风险 | 影响 | 处置 |
|---|---|---|
| `JwtInterceptor.afterCompletion` 鉴权失败时不执行，ThreadLocal 未清 | 线程复用可能读到上个 userId | 用户决定暂不修 |
| `user.openid` 排序规则大小写不敏感 | 理论上登录串号 | 用户决定暂不修 |
| 数据库凭据曾进 git 历史，MySQL 侧明文密码未处理 | 凭据泄露面 | 配置已移出版本库，历史/库侧未清理 |
| `CorsConfig` 放开所有来源 + allowCredentials | 仅开发期可接受 | 生产需收紧 |
| 未支付订阅消息 | 代码已完成、服务已部署 | 小程序发行后验证推送 |
| 公网延迟 ~0.9s | CDN（腾讯云 EdgeOne）跨境链路 | 待决定是否 DNS-only 直连 |
| Python `/recommend` 不存在 | 猜你喜欢走销量兜底 | 用户决定近期不实现 |
| `user.id_number` 空列 | 代码不引用 | 待手动 DROP，不阻塞 |
| 宝塔托管的 `html_xiaojiecpn.com.conf` 会被重写 | 宝塔保存站点配置会冲掉 443 SSL 块 → 商城首页 404 | 2026-10-31 已补 443 块；**需在宝塔打开该站点 SSL 开关**，否则复发 |
| 后台 jiemids.com 缺 SPA 回落 | Vue3 history 路由刷新即 404（首页正常，子路由白屏） | 2026-10-31 已在 `extension/jiemids.com/spa.conf` 加 `try_files $uri $uri/ /index.html` |
| 登录失败**时序侧信道**：账号不存在时 `admin == null` 短路，不执行 BCrypt，比「密码错」快约 100ms | 攻击者可用响应耗时枚举出哪些管理员用户名存在 | **2026-11-03 决定暂不改**；修法见下方「后台登录加固（待做）」 |
| 登录失败锁定按 `username+ip` 计，挡不住「一个 IP 遍历一堆用户名」的枚举 | 每组合只错 1 次永不触发锁定，还能把 Redis key 堆爆 | **2026-11-03 决定暂不改**；需补 IP 维度限流（`auth.login.max-fail-per-minute`） |
| 验证码 `get` 成功但 `delete` 抛异常 → 整段 catch 成 500 | 验证码明明正确却报「验证码服务暂不可用」，且该验证码没被消费可重放 | **2026-11-03 决定暂不改**；应把读失败与删失败分级（删失败只告警） |
| 验证码校验失败**不计入**登录失败计数（校验在密码之前抛异常） | 连续输错验证码时计数不涨，用户观感是「锁不上」；实测密码错 5 次必锁（429），验证码错则一次都不计 | **2026-11-03 实测确认，维持现状**；详见「后台登录锁定实测」 |

## 后台登录加固（2026-11-03 讨论结论，**暂未实施**）

- **登录方法顺序保持现状**（`AuthServiceImpl.login`：参数判空 → `assertNotLocked` 锁定检查 → `validateCaptcha` 验证码 → `findByUsername` + 验密 → 失败计数 → 停用检查 → 签发 token）。杰米确认这个思路正确：**先验验证码再查库，验证码都不对就没必要跑一趟数据库**。曾讨论过的「把查库提到验证码校验之前」已否决 —— 行为等价，还会让乱填验证码的请求也打一次 DB（验证码是脚本最爱乱填的一环，等于把它变成 DB 放大器），并且会破坏「账号不存在就跳过验证码」这类推论（`validateCaptcha` 无论对错都删 Redis key，验证码照样被消费，跳过反而破坏一次性语义）。
- **企业级真正该补的三层**（按性价比，均未做）：
  1. **消除时序差**（最便宜、必做）：账号不存在时也跑一次 dummy BCrypt（`admin == null` 时用固定假哈希比对），让两条路径耗时一致。理由：枚举成本是线性于账号数的 —— 每个用户名一次请求，BCrypt 100ms 一个，几千账号单线程八分钟就能测完整份名单，**账号越多这个口子越值钱，不是越不值钱**；要防的不是「账号存在性本身」（OWASP 认为用户名可枚举不算高危），而是「不用猜密码就能拿到名单的免费旁路」。
  2. **IP 维度速率限制**（几千账号时最关键）：现有锁定 key 是 `auth:login:fail:{username}:{ip}`，攻击者拿一个 IP 遍历 5000 个用户名、每个只试 1 次密码，**每个组合各自只错 1 次、永远不触发锁定**，还能顺手堆 5000 个 Redis key。需另加 `auth:login:fail:ip:{ip}`（1 分钟窗口、阈值可配，如 20 次/分钟），且**账号不存在时也要计入 IP 计数**。
  3. **审计日志 + 枚举行为告警**：同一 IP 短时间大量**不同**用户名失败是典型枚举特征，现在只有 warn 日志，无告警。
- **更上层的现实判断**：上千账号通常意味着该上 IdP（LDAP / OAuth2 / 企业微信扫码），账号策略、MFA、锁定、审计全交给 IdP，自研登录的边际收益迅速变负；上面 1+2 两条足以覆盖「企业级」诉求（MFA 目前也没有）。

## 后台登录锁定实测（2026-11-03，本地）

- **结论：锁定机制本身是好的，"没锁"是验证码分支导致的观感错觉。**
- **实测（直连 9001 与经网关 8080 两条路径都验过，每次都换新验证码）**：第 1~4 次返回 `401 用户名或密码错误`，Redis 计数 `auth:login:fail:admin:{ip}` 从 1 涨到 4（ttl=600）；**第 5 次返回 `429 尝试次数过多，请 10 分钟后再试`**；锁定后**用正确密码再试仍然是 429**（要等满 10 分钟，这是防爆破的正确行为）。
- **关键对照实验**：验证码填错时返回 `401 验证码错误或已过期`，而**失败计数 key 完全为空 —— 验证码错误根本不计入失败次数**（`validateCaptcha` 在密码校验之前就抛了，走不到 `recordLoginFailure`）。所以浏览器里只要有一次卡在「验证码错误或已过期」，计数就永远停在 1，**永远触发不了锁定**。前端 `Login.vue` 失败后会 `loadCaptcha()` 刷新验证码图并清空输入框，很容易让人继续输错新码 —— 这就是"试了半天没锁"的来源。
- **三个容易踩的行为特征**：① 锁定后正确密码也进不去，必须等满 10 分钟；② 计数 key 带 IP，经网关时 clientIp 是网关地址（本地 `192.168.92.1`），换网络/换 IP 计数从头开始；③ 账号不存在也会计数（统一回「用户名或密码错误」），所以枚举同样消耗锁定额度。
- **若要"输错密码 5 次必锁"这种确定性**：得让验证码失败也计入锁定额度，但会误伤「看错验证码」的真实用户 —— 目前维持现状更合理；真要收紧，优先做的是上面「后台登录加固」里的 IP 维度限流。
- **锁定提示语已改为带次数**（2026-11-03，杰米指出缺提示）：原先只有「尝试次数过多，请 10 分钟后再试」，**没说用户错了几次**；现在统一走 `lockedMessage(failures, ttlSeconds)` → `String.format("登录失败次数已超过%d次，已被锁定，请%d分钟之后再试", failures, minutes)`。两个调用点：`assertNotLocked`（已锁定被拦，带剩余分钟数）与「密码错刚好到第 5 次」（带失败次数、按完整锁定时长提示）。前端 `request.js` 的 429 分支是**直接展示后端 message**，所以后端改完前端无需改。实测：第 1~4 次 `401 用户名或密码错误`，第 5 次 `429 登录失败次数已超过5次，已被锁定，请10分钟之后再试`，锁定后再试返回同一句（剩余 599 秒 → 向上取整 10 分钟）。


## 后台账号软删除（2026-11-01）

- **为什么软删**：后台账号没有外键依赖，但物理 DELETE 会丢掉历史操作痕迹，且 C 端 `user` 表已用 `is_deleted`（注销）做先例，保持口径一致。软删 = `is_deleted=1` + `status=0`，账号从列表消失、登录被拒、旧 token 立即失效，数据仍留在表里可追溯。
- **四处过滤必须改全**：`findByUsername` / `findById` / `listAdmins` / `countAdmins` 都加了 `is_deleted = 0`。漏掉任何一处都会导致「已删账号还能登录」或「列表里还显示」。
- **登录路径的天然拦截**：`AuthServiceImpl.login` 用的是 `findByUsername`（已带过滤），软删账号直接查不到 → 走「用户名或密码错误」分支，不会泄露账号是否存在；`status=0` 的停用账号仍走原有的 403「账号已停用」。
- **令牌失效用现成机制**：复用 `AdminTokenInvalidator.invalidateAfterCommit`（写 Redis `auth:admin:invalid-before:{id}`，事务提交后才写，Redis 异常 fail-open）。注意 `isInvalid` 是严格比较 `issuedAt < invalidBefore`，**同一秒签发的 token 最多还能活 1 秒**，属已知边界，不算 bug。
- **用户名唯一性要查全量**：唯一性检查改用 `findByUsernameIncludingDeleted`（不过滤），已删账号占用用户名时提示「用户名已被删除的账号占用，请换一个」—— 直接沿用 `findByUsername` 会误报「用户名已存在」，让管理员以为换名字就行。
- **红线**：`id=1`（超管）和当前登录账号都禁止删除，前端按钮直接不显示、后端再校验一次（不能只靠前端隐藏）。
- **生产执行方式**：SQL 用 `ssh ... mysql < docs/sql/add-admin-soft-delete.sql` 管道传入，**不在生产机写文件**；脚本幂等（先查 `information_schema.COLUMNS` 再决定是否 ALTER），重复执行安全。

## 工作台用户统计口径（2026-11-03）

- **数据来源**：后台工作台「用户增长」图 **不是前端算的**。链路：`useWorkplaceData.js` → `GET /api/admin/users/statistics?period=` → user-service `AdminUserController.statistics` → `UserServiceImpl.getUserStatistics` → `UserMapper.countNewUsersByBucket` / `countTotalUsers`（两条注解 SQL，**同一张 `user` 表、同一过滤 `is_deleted=0`**）。字段是驼峰（该服务没加 `@JsonNaming(SnakeCase)`，只有 product-service 加了）。
- **两条线的口径**：`newUsers` = 窗口内**每个时间桶的新注册数**（增量）；`totalUsers` = 全表未注销**用户总数**（存量，一个标量）。
- **数值上不可能越界**：两条 SQL 同表同过滤 → 窗口内新增是全量的子集，恒有 `Σ新增 ≤ totalUsers`。生产实测：65 人、近 7 天新增 37（10-28 起 7/6/5/4/7/7/1）→ 累计 28→35→41→46→50→57→64→65，从未超过总量。
- **"看起来越界"是双轴视觉误导**：`LineChart` 双轴各自缩放（左轴=用户总量 28~65、右轴=新增用户 0~7），而 `UserGrowth.vue` 传了 `:show-right-axis-label="false"` 把右轴刻度数字藏掉 → 10-28 / 11-01 / 11-02 三天新增线画在累计线之上；两条线量纲还不同（累计存量 vs 单桶增量），本来就不能比高低。**最终改法（用户拍板，两轮）**：**去掉双轴，两条序列共用一根纵轴** —— 「用户总量」= 存量累计走**折线**，「新增用户」= **每桶新增**（不累计）走**柱状**，柱子高度就是那天的量。
- **中间踩过的弯路（已废弃，勿再走）**：曾一度改成「两条线都画累计」——「用户总量」= 存量累计、「累计新增」= `newUsers` 的前缀和，两条都是单调递增的台阶，**形状几乎一样，第二条只是第一条平移，信息量极低**（等于把 `newUsers` 累加一遍再画，纯重复造轮子），而且看不出「今天新增了几个」。**用户拍板改回离散量**，并把 `cumulativeNewUsers` 字段从后端删掉（它是未发版的新增字段，删掉零风险）。
- **折线 + 柱状是这组数据的天然画法**：存量是连续量（折线表达趋势），每日新增是离散量（柱状表达「那一天的量」），共用一根轴 0~65 —— 每日新增的柱子偏矮（约轴高 10%），但**总量曲线绝不会被误读成被盖过**，这比藏右轴刻度诚实得多。`LineChart` 组件为此加了 `type` 支持（序列可指定 `'line'` / `'bar'`，柱状 `barMaxWidth: 24`），其余图（营业额、订单统计）不传 `type` 仍是折线，行为不变。
- **累计存量曲线由后端给**（保留方案 A 的这一半）：`UserStatisticsVO` 新增 `cumulativeUsers`（存量累计），`UserServiceImpl.getUserStatistics` 一次算完，前端只渲染、保留老接口兜底。**不变量**：`cumulativeUsers` 单调递增且**末值恒等于 `totalUsers`**（窗口覆盖到今天时）。
- **别混淆的两处「新增用户」**：工作台这个是**新注册用户**（`user` 表 `create_time`）；Excel 经营报表里那个是**当天下单的新用户**（order-service `ReportServiceImpl` → `orderMapper.countNewUsersByDay`，按订单 `user_id` 去重），口径不同。
- **本地「看不到图」≠「没有数据」**（2026-11-03 排查）：`LineChart` 无数据时渲染的是 `<a-empty description="暂无数据">`。本地 IDEA 里跑的 user-service 是改代码**之前**启动的旧进程，`GET /api/admin/users/statistics` 返回 500 → 前端 `loadUserStatistics` 走 catch、`userGrowth` 保持空数组 → 页面显示「暂无数据」，看起来像"这段时间没人注册"。定位依据：同一旧进程的其它 DB 接口（`/api/admin/member-levels`、`/api/admin/members`）都 200，**只有 statistics 500**；用最新 jar 起临时实例，同库、同 SQL、同参数直接 200 且两个累计字段都对 → 是旧进程加载的 class 状态问题，**重启 user-service 即恢复**。
- **前端补了错误态**（2026-11-03）：接口失败不再伪装成「暂无数据」——`useWorkplaceData.js` 新增 `userGrowthError`，失败置 `true`（同时保留上一次数据，图表不塌成空白）；`UserGrowth.vue` 收到 `error` 时显示「用户统计加载失败，请刷新重试」并占满图表高度（400/340/260）。否则运营会把接口故障误读成业务结论。


## 资源

| 项目 | 本地路径 | 说明 |
|---|---|---|
| 小程序 | `D:\study\my-workspace\wechat-workspace\e-commerce-uniapp` | C 端，对应 `/api/**` |
| PC 商城 | `D:\study\my-workspace\wechat-workspace\ecommerce-mall` | Vue3 + Vite SPA，https://www.xiaojiecpn.com |
| 后台前端 | `D:\study\my-workspace\wechat-workspace\e-commerce` | Vue3 + ant-design-vue |
| Python AI | `D:\study\my-workspace\wechat-workspace\recommender-python` | 导购机器人 |
| 后端 | `D:\study\my-workspace\wechat-workspace\e-commerce-boot` | Spring Cloud 多模块 |

- API：`https://api.jiemids.com`；后台：`https://www.jiemids.com`；PC 商城：`https://www.xiaojiecpn.com`
- 接口文档：网关 `/swagger-ui.html`，各服务 `/v3/api-docs`（`SPRINGDOC_ENABLED=true`）
- 本地配置：`application-dev.yml`（gitignored）；生产配置：`/www/java/.env`
