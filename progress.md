# 进度日志

## 会话：2026-09-28

### 阶段 1：项目结构与配置梳理
- **状态：** complete
- **开始时间：** 2026-09-28
- 执行的操作：
  - `git status` / `git log` / `git diff --stat` 摸清仓库状态（分支 `master`，最近提交 ee90659「0927」）
  - 列出 `src` 下全部文件（21 个 main Java + 1 个 test Java）、`docs/`、`resources/`
  - 读 `pom.xml`、`application.yml`、`.gitignore`、`.claude/settings.local.json`
  - 读 `project_spec.md`（架构约定）
- 创建/修改的文件：
  - 无（只读）

### 阶段 2：核心链路梳理
- **状态：** complete
- 执行的操作：
  - 读 user 模块全部代码：`WxLoginController`、`UserController`、`UserService(+Impl)`、`UserMapper(+XML)`、`User`、`WxLoginDTO`、`WxLoginVO`、`UserProfileVO`
  - 读框架层：`JwtInterceptor`、`WebMvcConfig`、`CorsConfig`、`OpenApiConfig`、`JwtUtil`、`UserContext`、`Result`、`GlobalExceptionHandler`、`BusinessException`、`HttpClientUtil`、`Application`、两个 Properties
  - 读 `docs/openapi.yaml` 与 `src/test/.../TempTimeoutVerify.java`
  - 梳理出登录 / 鉴权 / 资料 / 头像上传四条链路，写入 `findings.md`
- 创建/修改的文件：
  - 无（只读）

### 阶段 3：遗留问题与不一致盘点
- **状态：** complete
- 执行的操作：
  - 对照 `project_spec.md` 的待办表与代码，列出 9 项遗留 / 不一致
  - 核对代码与 `docs/openapi.yaml`：发现 415 未进文档、`WxLoginVO.avatar` 与 `UserProfileVO.avatar` 语义差异未在 Java `@Schema` 体现
  - 用 `git diff --cached` + `git diff HEAD` 还原未提交改动的真实内容，发现工作区把 `UserController` 注释/封装改回粗糙版本，且暂存区是一份「类体被清空」的版本
- 创建/修改的文件：
  - 无（只读）

### 阶段 4：生成 planning 文件
- **状态：** in_progress
- 执行的操作：
  - 写 `task_plan.md`（5 个阶段 + 4 个待确认问题）
  - 写 `findings.md`（技术栈 / 结构 / 接口 / 链路 / 约定 / 遗留 / 未提交改动）
  - 写 `progress.md`（本文件）
- 创建/修改的文件：
  - `task_plan.md`（新增）
  - `findings.md`（新增）
  - `progress.md`（新增）

### 追加：并入 `project_spec.md` 并删除原文件
- **状态：** complete
- **时间：** 2026-09-28（同一会话，用户追加指示）
- 执行的操作：
  - 先确认原文件已入库（提交 `ee90659`）且无未提交改动，删除可回滚
  - 就「合并到哪个文件」向用户确认 → 用户选择**并入 `findings.md`**
  - 把 `project_spec.md` 全部正文（三端划分 / 核心原则 / 目录结构 / 命名与路径规则 / 各层拆分规则 / 身份模块例外 / 迁移落地顺序 / 接口文档同步约定 / 头像上传遗留待办）以「项目架构约定（原 `project_spec.md`）」一节并入 `findings.md`，标题层级降一级
  - 用脚本逐行比对原文件与并入段落：**127 行正文完全一致**，仅多出章节标题行 → 确认无内容丢失后才删除
  - `rm project_spec.md`（**未**用 `git rm`）：刻意不写索引，避免打扰用户已有的暂存区状态
  - 同步改掉 `findings.md`、`task_plan.md` 里指向 `project_spec.md` 的 8 处引用
  - 更新记忆 `shared-backend-architecture` 与 `MEMORY.md` 的指针到新位置
- 创建/修改的文件：
  - `findings.md`（新增「项目架构约定」一节 + 8 处引用改写）
  - `task_plan.md`（引用改写 + 阶段 4 增一条勾选）
  - `progress.md`（本段）
  - `project_spec.md`（**已删除**，工作区删除未入索引；恢复：`git checkout -- project_spec.md`）
  - 记忆文件 `shared-backend-architecture.md`、`MEMORY.md`

### 阶段 5：后续开发
- **状态：** pending
- 执行的操作：
  - 未开始，等用户定优先级

## 会话：2026-09-30

### 阶段 A：探索 Node 后端 + 前端（只读）
- **状态：** complete
- 执行的操作：
  - 列出 `e-commerce/server` 源码（controllers/routes/sqlite/middleware/util，共 12 个业务域），读 `index.js`/`package.json`/`API.md`
  - 读 `sqlite/database.js`/`shopTables.js`，确认「多租户店铺沙箱」架构（SHOP_TABLES 17 张表、模板店、号段隔离、沙箱回收）
  - 读前端 `src/api/*.js` 全部接口路径 + `request.js`（baseURL、401/403 拦截）
  - WebSearch 确认 Spring Cloud Alibaba / Nacos 对 Spring Boot 4 的版本兼容
- 创建/修改的文件：无（只读）

### 阶段 B：向用户确认关键决策
- **状态：** complete
- 确认项：
  - 多租户沙箱 → **保留**
  - 微服务落地程度 → **真·多服务部署**
- 结果：已记录到 `task_plan.md`「已确认决策」与 `findings.md`「Node 后端重构探索」

### 阶段 C：固化规划文件
- **状态：** complete
- 创建/修改的文件：
  - `task_plan.md`（重写为「Node 后端重构 + 微服务拆分 + 前端对接」，含 8 服务拆分、多租户落法、阶段 0–7 路线）
  - `findings.md`（新增「Node 后端重构探索」一节 + 更新「视觉/浏览器发现」）
  - `progress.md`（本段）

### 阶段 D：搭微服务骨架（8 服务 Maven 多模块）
- **状态：** complete（骨架 + 编译通过；Nacos 注册/路由验证待 Docker 起 Nacos 后做）
- 执行的操作：
  - 父 `pom.xml` 改为聚合父工程（packaging=pom + modules + Spring Cloud 2025.1.3 / SCA 2025.1.0.0 BOM）
  - 新建 `common` 模块下沉 13 个框架类（包名不变）
  - `user` 模块迁入 `user-service`（服务名/包名保持 user，不叫 member），启动类 `UserServiceApplication`，端口 9006
  - 新建 6 个占位服务（auth 9001 / shop 9002 / product 9003 / order 9004 / aftersale 9005 / file 9007），各含 pom + 启动类 + application.yml（Nacos 注册 + jwt/wechat 占位值以满足 common 的 @PostConstruct 校验）
  - 新建 `gateway`（WebFlux + gateway-server-webflux + loadbalancer + nacos-discovery），路由 `lb://user-service` Path=/api/**
  - 删除根目录残留 `src`（Application.java / templates/index.html / TempTimeoutVerify.java）
  - `.gitignore` 补 `upload/`
  - `mvn -B clean compile`：10 模块全部 BUILD SUCCESS
- 创建/修改的文件：父 `pom.xml`、`common/`、`user-service/`、`gateway/`、`auth-service/`、`shop-service/`、`product-service/`、`order-service/`、`aftersale-service/`、`file-service/`、`.gitignore`、`task_plan.md`、`progress.md`
- 修正：用户确认商品不做多规格、无 SKU，清除 `task_plan.md`（拆分表 / Feign 关系 / 阶段 3）、`findings.md`（业务域描述）、`product-service`（pom 描述 + 启动类注释）中的 SKU 措辞；商品改为单规格，价格/库存直接挂商品上，不做 `product_skus` 表与笛卡尔积生成

### 阶段 E：Docker + Nacos 部署，阶段 0 验证通过
- **状态：** complete
- 执行的操作：
  - Docker Desktop 安装（用户级，装在 `%LOCALAPPDATA%\Programs\DockerDesktop`，非默认 `C:\Program Files\Docker`），`resources\bin` 已进用户 PATH（新终端生效）
  - 写 `docker-compose.yml`：只起 Nacos（MySQL 复用本机 MySQL80，3306 已占用、库 `e_commerce_db` 已建好）；端口映射 8848 / 9848-9849 / 18080→8080
  - 踩坑并解决：① 镜像 tag 带 v 前缀（`v3.1.1` 非 `3.1.1`）；② Nacos 3.1.1 启动脚本强制 `NACOS_AUTH_TOKEN`/`IDENTITY_KEY`/`IDENTITY_VALUE` 非空（关鉴权也得设，否则 exit 255）；③ 3.x 开鉴权后 `nacos/nacos` 登录报 User not found → 开发期关鉴权 `NACOS_AUTH_ENABLE=false`
  - `mvn -q install -DskipTests` 全量装本地仓库（父 pom + 9 模块，解决 `e-commerce-boot:pom absent`）
  - 起 auth-service / user-service / gateway 三个服务，均注册到 Nacos 成功（`nacos registry ... register finished`）
  - 网关路由验证：`curl localhost:8080/api/user/profile` 与直连 `localhost:9006` 返回一致（HTTP 401 + Result 结构），`lb://user-service` 打通
- 创建/修改的文件：
  - `docker-compose.yml`（新增）
  - `task_plan.md`（阶段 0 打勾完成，状态 completed）
  - `findings.md`（新增「阶段 0 实测结果」节）
  - `progress.md`（本段）

### 阶段 F：auth-service 登录接口（阶段 1 起步）
- **状态：** in_progress（代码就绪 + 编译通过；待建表脚本执行后做端到端验证）
- 执行的操作：
  - 梳理前端登录契约：`POST /api/login`（`{username,password}`）→ `{success,data:{token,user}}`；前端解 token payload 读 `username`/`role` 做 `isAdmin()` 判断；演示账号 admin/admin123（模板店 shopId=1）
  - `JwtUtil.generateAdminToken`：claims 额外带上 `id`/`username`/`role`/`shopId`（前端解 payload 所需）
  - auth-service 补 MyBatis + MySQL 依赖、数据源/MyBatis 配置、`@MapperScan`
  - 新增 Admin 实体 / AdminMapper(+XML) / AuthService(+Impl) / AuthController（`POST /api/login`、`GET /api/user/info`、`POST /api/logout`）
  - `WebMvcConfig.EXCLUDE_AUTH_PATHS` 补 `/api/logout`（无状态登出免鉴权）
  - gateway 路由补 auth-service：`/api/login,/api/logout,/api/user/info` → `lb://auth-service`（放在 user-service 兜底之前）
  - 写 `docs/sql/admin.sql`（建 admin 表 + 种子数据，幂等）—— 按「禁止代跑 DB 初始化」约束，未执行
  - `mvn install`：10 模块全部 BUILD SUCCESS
- 创建/修改的文件：
  - 新增：auth-service 下 `Admin` / `AdminMapper` / `AdminMapper.xml` / `AdminLoginDTO` / `AdminUserVO` / `AdminLoginVO` / `AuthService` / `AuthServiceImpl` / `AuthController` / `application-dev.yml`、`docs/sql/admin.sql`
  - 修改：`common` 的 `JwtUtil`（generateAdminToken）、`WebMvcConfig`（EXCLUDE_AUTH_PATHS）、auth-service `pom.xml` + `AuthServiceApplication` + `application.yml`、gateway `application.yml`
- 待办/待确认：
  - 需执行 `docs/sql/admin.sql` 建表（或授权我执行）
  - **路径约定待定**：findings.md 约定后台接口走 `/api/admin/**`，但实际前端全部走 `/api/**`（无 admin 前缀），导致 `JwtInterceptor.isAdminPath` 按路径分流会把 admin 令牌在 `/api/user/info` 等非 `/api/admin` 接口上判成「需 user 类型」→ 403。需决策：改前端前缀 vs 改后端按 role 判断

### 阶段 G：商品浏览免登录 + product-service 接口骨架
- **状态：** complete（代码 + 编译通过；product-service 未实际启动做端到端验证）
- 执行的操作：
  - 调整鉴权：`WebMvcConfig.EXCLUDE_AUTH_PATHS` 新增 C 端商品浏览路径（`/api/products`、`/api/categories`、`/api/tags` 及其 `/**`），实现「进首页看商品不带 token，加购/下单才需登录」
  - 关键配套：`OpenApiConfig` 的免登录判断从 `contains` 精确匹配改为 `AntPathMatcher` 匹配，与拦截器 `excludePathPatterns` 语义一致，避免清单出现 `/api/products/**` 后文档与拦截器漂移
  - gateway 路由补 product-service（`/api/products/**`、`/api/categories/**`、`/api/tags/**`），放在 user-service 的 `/api/**` 兜底之前
  - product-service 建分层骨架（controller/service/impl/vo），返回固定示例数据（不接库）：`ProductController` 4 个 GET 接口 + `ProductServiceImpl` 占位数据 + `ProductVO`/`CategoryVO`/`TagVO`
  - `docs/openapi.yaml` 补「商品浏览」标签、4 个接口定义（均 `security: []` 免登录）、`NotFound` 响应、7 个 schema
  - `findings.md` 同步三处（免登录清单说明、鉴权链路、技术决策表新增一条）
  - `mvn -q -pl product-service -am compile` 通过
- 创建/修改的文件：
  - 新增：`product-service` 下 `controller/ProductController`、`service/ProductService`、`service/impl/ProductServiceImpl`、`vo/ProductVO`、`vo/CategoryVO`、`vo/TagVO`
  - 修改：`common` 的 `WebMvcConfig`、`OpenApiConfig`；`gateway/application.yml`；`product-service/application.yml`（springdoc 注释）；`docs/openapi.yaml`；`findings.md`
- 待办/待确认：
  - 商品数据为占位（`DEMO_*`），接库时（阶段 2/3 设计 products/categories/tags 表并接 MyBatis）替换 `ProductServiceImpl` 即可，Controller/VO/文档结构不动
  - product-service 尚未加 MyBatis/MySQL 依赖与 `@MapperScan`，接库时一并补

## 会话：2026-10-01

### 阶段 H：商品域数据库迁移（SQLite → MySQL）+ 图片迁移
- **状态：** complete（数据 + 图片已迁入、product-service 接库；待重启 gateway/product-service 做端到端验证）
- 执行的操作：
  - 写 `docs/sql/product.sql`：商品域 4 表（shops/categories/tags/products）建表 + 种子数据（模板店 shopId=1：32 商品含下架、22 分类、1 标签）
  - 迁移设计：去掉 ID_BLOCK 号段 hack → BIGINT AUTO_INCREMENT；Unix 秒 → DATETIME（FROM_UNIXTIME）；REAL → DECIMAL；categories.product_count 去掉（读走子查询）、tags.product_count 保留（物化计数）；不加物理外键
  - 图片迁移：`server/uploads/` 的 32 张 png/jpeg 用 Pillow 转 webp → `upload/image/upload/`；`WebMvcConfig` 加 `/image/**` 映射、gateway 加 `/image/**` 路由；修 id=9 的 image 从 UUID 改回数字命名
  - product-service 接库：entity/mapper/XML/ProductServiceImpl 换真实查询（`TEMPLATE_SHOP_ID=1L`），application.yml 加 datasource/MyBatis 配置，启动类加 `@MapperScan`
- 踩坑（详见 findings.md「商品域数据库迁移踩坑点」）：GBK 乱码、幂等脚本局限、图片路径/格式错位、索引决策
- 创建/修改的文件：
  - 新增：`docs/sql/product.sql`、product-service 下 entity/mapper/XML、`application-dev.yml`
  - 修改：product-service 的 `ProductServiceImpl`/`ProductVO`/`CategoryVO`/`TagVO`/`pom.xml`/`application.yml`/`ProductServiceApplication`、common 的 `WebMvcConfig`、gateway `application.yml`、findings/progress/task_plan 三个 md

## 会话：2026-10-03

### 阶段 I：AI 导购 Agent 服务（ai-agent-service）
- **状态：** in_progress（Agent 骨架 + 对话 + 工具调用已验证；真实推荐/下单待下游服务）
- 执行的操作：
  - 读文档《智能商品推荐助手项目.docx》，定位为「完整 Agent 而非 LLM 聊天壳」，保留 Graph 工作流 / RAG / MCP / Function Calling 四大能力
  - 技术选型确认：Spring AI 2.0.1（官方，支持 Boot 4.1）而非 Spring AI Alibaba（稳定版 1.1.x 不兼容 Boot 4）；LLM 走硅基流动 OpenAI 兼容接口（deepseek-ai/DeepSeek-V4-Flash）
  - 新建 ai-agent-service 微服务（端口 9008）：pom（spring-ai-starter-model-openai + mcp-server-webmvc + vector-store + openfeign + loadbalancer）、启动类、application.yml/dev（key 放 dev 文件，已 gitignore）
  - 分层实现四大能力：agent/IntentRouter（意图路由）、tool/ProductTool + OrderTool（@Tool）、rag/DocumentLoader + EmbeddingService + RagRetriever（RAG，SimpleVectorStore 起步）、mcp/McpServerConfig（@McpTool）、service + controller（ChatClient 编排 + SSE 流式）
  - 父 pom 加模块 + spring-ai-bom 2.0.1；gateway 加路由 + swagger 聚合；common WebMvcConfig 加 /api/agent 免登录
  - 实测验证：启动 + Nacos 注册成功；对话（ChatClient 调 DeepSeek 返回真实回复）；Tool Calling（触发 searchProducts）；SSE 流式（Flux<String> 打字机输出）；MCP（Registered tools: 2）
- 关键确认：
  - Spring AI 2.0 配置扁平化（`spring.ai.openai.chat.model`，不再嵌套 `.options`）；@Tool/@McpTool 包名 `org.springframework.ai.tool.annotation` / `org.springframework.ai.mcp.annotation`
  - 商品推荐返回 DeepSeek 编的键盘（幻觉）：product-service 未启动、Feign 调不到真实数据 → 印证「接真实数据降幻觉」
- 创建/修改的文件：
  - 新增：ai-agent-service 整个模块（19 个文件）
  - 修改：父 pom.xml、gateway/application.yml、common/WebMvcConfig.java、findings.md、task_plan.md、progress.md
- 待办/待确认：
  - 真实商品推荐端到端（product-service 启动后验证）
  - AI 下单闭环（依赖 order-service 下单接口，阶段 4）
  - RAG 生产化（SimpleVectorStore → Milvus/PgVector）

## 测试结果

| 测试 | 输入 | 预期结果 | 实际结果 | 状态 |
|------|------|---------|---------|------|
| 编译 | `mvn -B clean compile` | 编译通过 | **通过**（10 模块全部 BUILD SUCCESS，user-service 11 源文件） | 通过 |
| 单元测试 | `mvnw test` | — | **未执行** | 未运行 |
| `TempTimeoutVerify.testReadTimeoutTakesEffect` | 本地假服务 10s 不回包 | 5s 读超时后失败 | **未执行**（该测试需真实起 Spring 上下文 + 本地端口） | 未运行 |

> 说明：本次任务只做代码阅读与文档生成，按规范未执行任何数据库操作；上面的测试均未运行，未做任何「验证通过」的断言。

## 错误日志

| 时间戳 | 错误 | 尝试次数 | 解决方案 |
|--------|------|---------|---------|
| 2026-09-28 | `git diff` 的输出与 `git status` 的 `MM` 状态对不上，无法判断哪份改动是真的 | 1 | 改用 `git diff --cached`（HEAD→暂存区）与 `git diff HEAD`（HEAD→工作区）分别比对；确认存在两份叠加的改动，已记录到 `findings.md` 第 8 节 |
| 2026-09-30 | `wsl --install` 报「不支持此接口」；`wsl --update`/`--version` 均打印用法帮助——根因是系统为 inbox 旧版 WSL，且 Store 通道不通 | 2 | 用 `wsl --install --no-distribution --web-download` 从网上下载新版 WSL（绕开 Store）；`--inbox` 只是启用旧版功能、非新版 |
| 2026-09-30 | `docker compose up -d` 报 `nacos/nacos-server:3.1.1: not found`（我 shell 里 `docker` 也一度 not found） | 1 | ① 镜像 tag 需带 v 前缀 → `v3.1.1`；② 我的 Bash PATH 未含 DockerDesktop，用户 PowerShell 正常（新终端生效即可） |
| 2026-09-30 | Nacos 容器 `exit 255`：`NACOS_AUTH_TOKEN must be set` / `IDENTITY_KEY must be set` | 1 | Nacos 3.1.1 启动脚本强制三个鉴权变量非空（关鉴权也得设），补齐后重启 |
| 2026-09-30 | auth-service 注册/控制台登录报 `User not found! Please check user exist or password is right!`（nacos/nacos） | 1 | Nacos 3.x 默认账号机制与 2.x 不同，开发期直接 `NACOS_AUTH_ENABLE=false` 关鉴权 |
| 2026-09-30 | auth-service 启动报 `Could not find artifact ecommerce:e-commerce-boot:pom` | 1 | 父 pom 未装本地仓库，`mvn -q install -DskipTests` 全量装一次 |
| 2026-09-30 | 小程序 `GET /api/user/profile` 带合法 token 经网关 500「服务器内部错误」 | 3 | 根因：三个服务是 IntelliJ 13:02–13:04 起的旧进程，早于阶段 F 代码（13:47–13:48 写完+install）。同 classpath 新起实例即 200，证明代码/DB 无问题。从最新 jar 重启 user-service/auth-service/gateway 后 profile 200、`POST /api/login` 由 404 变 500（admin 表未建，属待办） |
| 2026-10-01 | `mysql < product.sql` 导入后中文乱码（商品名/分类名/表列 COMMENT 变「鍟嗗搧」） | 1 | 根因：Windows mysql 客户端默认 gbk，UTF-8 文件被误解码。清空 4 表 + 加 `--default-character-set=utf8mb4` 重导解决；根治改 my.ini `[client]` 加 `default-character-set=utf8mb4` |

## 五问重启检查

| 问题 | 答案 |
|------|------|
| 我在哪里？ | 阶段 1 进行中：profile 500 已修复（重启旧进程）、auth-service 登录接口已部署；下一步建 admin 表 + 端到端登录验证 |
| 我要去哪里？ | 阶段 1：gateway（CORS、JWT 过滤器、X-Shop-Id 透传、限流）+ auth-service（login/info/logout + RBAC） |
| 目标是什么？ | 拆成真·微服务部署，跑通 Nacos 注册 + Gateway 路由，再逐个补业务服务 |
| 我学到了什么？ | 见 `findings.md`（Nacos 3.x 部署踩坑：tag 带 v、鉴权变量强校验、默认账号机制变；技术栈 Boot 4.1 + Spring Cloud 2025.1.3 + SCA 2025.1.0.0 已验证兼容） |
| 我做了什么？ | 阶段 A–F：读 Node 后端/前端、确认决策、固化规划文件、搭 8 服务骨架、Docker 起 Nacos、auth 登录接口；修复 profile 500（重启三个旧进程，端到端 200） |

---
*每个阶段完成后或遇到错误时更新此文件*
