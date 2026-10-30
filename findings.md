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

## 后台 Admin 对接契约

- 后台写操作走 `/api/admin/**`，`JwtInterceptor` 按路径分流 admin；混用 `/api/products` 会撞免登录白名单。
- 字段以驼峰为主，**商品模块例外**：`AdminProductVO/AdminProductDTO` 用 `@JsonNaming(SnakeCase)` 返回蛇形；订单/库存/用户统计为驼峰。
- 分页返回 `Result<PageVO<T>>`（list/total/page/pageSize），前端读 `res.data.data.*`。
- 时间字段 VO 用 `createdAt/updatedAt`，`LocalDateTime` 加 `@JsonFormat("yyyy-MM-dd HH:mm:ss")`。
- 日期筛选收 `dateField/startDate/endDate`，`dateField` 走白名单（created_at/confirm_at/pay_at/ship_at/finish_at）防注入。
- 售后审核只改主表 `status/merchant_remark/updated_at`，业务时间戳按状态写子表列（refunding/returning→review_at、rejected→rejected_at、refund_confirming→refund_at、refunded→refunded_at、exchanging→exchange_at、exchanged→exchanged_at）；删除为主表/明细/子表 `is_deleted=1` 软删。

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

### 订单 / 库存 / 营收
- 下单：同一事务内查商品 → 校验并扣库存（`stock >= quantity` 乐观锁）→ 写订单/明细 → 清购物车；Redis 幂等键防重复下单。
- 状态机：1 待付款 → 2 待确认 → 3 待发货 → 4 已发货 → 5 已完成，6 已取消；取消/超时恢复库存，已支付未发货取消自动生成整单退款售后单。
- 定时任务：每分钟扫超时待付款订单取消并恢复库存；发货满 7 天自动完成。
- 营业额口径：实收（`pay_at` 区间 `pay_amount` 合计）− 退款（refunded 售后 `refund_amount`）；退款跟原订单 `pay_at` 归属；实收/退款都含取消订单。工作台今日/昨日按日期入参。
- 按天统计必须 `GROUP BY DATE_FORMAT(pay_at, '%Y-%m-%d')`，不能直接 `GROUP BY pay_at`；条件带 `is_deleted=0`。
- 未支付提醒：下单时用户授权 → 第 20 分钟一次性订阅消息 → 点击跳 `pending-pay?orderNo`；access_token 走 Redis 共享缓存。

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
- 线上 https://www.xiaojiecpn.com ，站点根 `/www/wwwroot/xiaojiecpn.com`；Nginx 配置 `extension/xiaojiecpn.com/mall.conf`（源文件 `ecommerce-mall/deploy/xiaojiecpn-mall.conf`）：`/api`、`/image`、`/upload` 同源代理到 `127.0.0.1:8080` + `try_files $uri $uri/ /index.html`。
- 已上线：首页（分类 + 猜你喜欢）、商品列表/分类、商品详情、搜索，均接真实 C 端接口；游客可浏览。
- 发布：`npm run build` 后，在 e-commerce-boot 里 `export WEB_DIR=.../ecommerce-mall`、`export WEB_ROOT=/www/wwwroot/xiaojiecpn.com`、`./deploy/release-web.sh --no-build`（脚本会留 `.bak.<时间戳>` 备份）。`release-web.sh` 的完成提示已改为按实际 `WEB_ROOT` 输出。
- 约定：单店 B2C、不做商家入驻、不做 SKU；登录后置，首页/浏览不登录，加购/购买再登录。
- **Web 登录缺口**：`/api/auth/phone-login` 是后台 admin 登录，签 admin 令牌，C 端接口会拒绝；P2 需在 user-service 加 dev 专用 demo 登录（手机号 13800138000 → user 令牌）。
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

## 发布与部署

- TCR：`ccr.ccs.tencentyun.com`，命名空间 `jiemi-ecommerce`，**8 个仓库**（7 Java + ai-service）。
- 服务器 `/www/java`，SSH 别名 `ecommerce`（root@193.112.176.228，私钥 `~/.ssh/ecommerce.pem`）。
- 发布：`./deploy/release.sh <svc>`（Maven → docker build/push → SSH pull + `compose up -d --no-build --no-deps`）；`--no-build`、`--no-deploy`、`--dry-run`、`--rollback <tag>`。
- **发布顺序**：product → order → aftersale → user → ai → 后台前端 → gateway → **auth 最后**（auth 停止签发旧 claim 后旧服务会拒绝新 token）；`ALL_SERVICES` 已把 auth 排最后。
- 后台前端：`./deploy/release-web.sh`（npm build → tar 上传 → rsync 到 `/www/wwwroot/jiemids.com`，保留 `.env/.htaccess/.user.ini/.well-known/public`，自动留 3 份备份）。
- compose 每个服务同时有 `image:` 和 `build:`；服务器必须 `--no-build`。`docker-compose.override.yml` 由 release.sh 生成，固定各服务当前镜像，全量 `up -d` 不要带新 TAG。
- 服务器 `.env` 已有 `REGISTRY`；2026-10-29 补了 `MYSQL_HOST=host.docker.internal` / `MYSQL_PORT` / `MYSQL_DATABASE` 给 AI。
- `upload` 是宿主机目录 `/www/java/upload`，bind mount 到 product/user，需单独备份。
- 单店化备份：`/www/backup/e_commerce_db_before_single_shop_*.sql`（含 `Dump completed` 校验）。

## 已知风险 / 遗留

| 风险 | 影响 | 处置 |
|---|---|---|
| `JwtInterceptor.afterCompletion` 鉴权失败时不执行，ThreadLocal 未清 | 线程复用可能读到上个 userId | 用户决定暂不修 |
| `user.openid` 排序规则大小写不敏感 | 理论上登录串号 | 用户决定暂不修 |
| 数据库凭据曾进 git 历史，MySQL 侧明文密码未处理 | 凭据泄露面 | 配置已移出版本库，历史/库侧未清理 |
| `CorsConfig` 放开所有来源 + allowCredentials | 仅开发期可接受 | 生产需收紧 |
| 未支付订阅消息 | 代码已完成、服务已部署 | 小程序发行后验证推送 |
| 公网延迟 ~0.9s | Cloudflare 香港跨境 | 待决定是否 DNS-only 直连 |
| Python `/recommend` 不存在 | 猜你喜欢走销量兜底 | 用户决定近期不实现 |
| `user.id_number` 空列 | 代码不引用 | 待手动 DROP，不阻塞 |

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
