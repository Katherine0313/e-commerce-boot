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

## 会话：2026-10-07

### 阶段 J：Redis 分类树缓存（product-service）
- **状态：** completed（分类树缓存已实现并编译通过，待重启 product-service 端到端验证缓存生成）
- 执行的操作：
  - 接入 Redis：common 加 `spring-boot-starter-data-redis`（Lettuce），本地 Redis 5.0.14（`D:\Software\tools\redis\Redis-x64-5.0.14.1`，默认 127.0.0.1:6379 无密码）
  - 分类树缓存：`category:parent:{id}`（父分类 + children）+ `category:parent:ids`（父 id 数组），纯 JSON 序列化（StringRedisTemplate + ObjectMapper，无 @class）
  - 新建 `CategoryTreeVO`（树形 VO，含 children）；`GET /api/categories` 返回树形结构（原为扁平列表）
  - 前端适配树形：`CategoryPanel.vue` / `category.vue` 改用 `item.children` 提取二级分类
  - 商品不缓存（用户要求只缓存分类）；分类页懒加载按父类查商品（`GET /api/products?categoryId=x`，后端 `listOnSaleByCategory` join categories 按 parent_id 聚合）
- 关键确认：
  - Boot 4.1 Redis 配置前缀仍是 `spring.data.redis`（配置类改名 `DataRedisProperties`）
  - Jackson 3 序列化器是 `GenericJacksonJsonRedisSerializer`（非 Jackson 2 的「2」版本）；default typing 会因 `BigDecimal` 反序列化报 500，最终改纯 JSON 方案
- 创建/修改的文件：
  - 新增：`CategoryTreeVO.java`
  - 修改：`ProductServiceImpl`（树形缓存读写）、`ProductService`/`ProductController`（返回树形）、`ProductMapper`（加 listOnSaleByCategory）、`CategoryMapper`（回退 listByIds）、`common/pom.xml`（redis 依赖）
  - 删除：`common/.../RedisConfig.java`（default typing 方案弃用）
  - 前端：`api/product.js`（加 getProductsByCategory）、`CategoryPanel.vue`、`category.vue`
- 待办/待确认：
  - 重启 product-service 端到端验证缓存生成
  - 后台分类写接口接入时补「按父 id DEL + 维护 ids」的失效逻辑

## 会话：2026-10-08

### 阶段 K：订单域迁移 + 购物车后端（order-service）
- **状态：** in_progress（表+数据已迁、购物车已实现并编译通过；未启动端到端验证）
- 执行的操作：
  - 写 `docs/sql/order.sql`（orders/order_items/cart/consignee_address 4 表建表，幂等）+ `order-data.sql`（数据迁移，INSERT IGNORE）
  - 用户执行建表 + 数据迁移（orders 52 / order_items 76 / cart 1 / consignee_address 15）
  - order-service 接购物车：pom 补 mybatis/mysql、application.yml 补数据源、启动类加 `@MapperScan`；新增 Cart/Product 实体、CartMapper/ProductMapper(+XML)、CartService(+Impl)、CartController、AddCartDTO/UpdateQuantityDTO/ClearCartDTO/CartVO
  - gateway 补 `/api/cart/** → order-service` 路由（否则落到 user-service 兜底）
  - `mvn -pl order-service -am compile` 通过
- 关键决策：
  - 新表 `COLLATE utf8mb4_general_ci`（服务器 MySQL 5.7 不支持 0900_ai_ci）
  - 时间字段 `created_at/updated_at`（忠于原表 _at 风格）
  - 加物理外键（含 user_id → user.id）；order_items.product_id 不加外键
  - user_id 映射：插 3 个占位用户（id=5/6/7），订单 1:1 映射
  - 购物车快照同库直接查 products 表
- 创建/修改的文件：
  - 新增：`docs/sql/order.sql`、`docs/sql/order-data.sql`、order-service 下 entity(2)/mapper(2+2XML)/dto(3)/vo(1)/service(2)/controller(1)、`application-dev.yml`
  - 修改：order-service `pom.xml` + `application.yml` + `OrderServiceApplication`、gateway `application.yml`
- 待办/待确认：
  - order-service 未启动端到端验证（用户选择先不测）
  - 下单/支付/超时关闭未做（阶段 4 剩余）
  - consignee_address 接口归属：task_plan 归 user-service，表已随迁但接口未做

### 阶段 L：订单功能 + Docker MySQL（order-service）
- **状态：** in_progress（下单/状态流转/超时关闭已实现并编译通过；未启动端到端验证）
- 执行的操作：
  - 探索原 Node 订单设计理念：状态机（6 状态+流转）、状态自动记时间戳、30 分钟超时关闭+并发二次确认、取消 vs 超时关闭的售后差异、库存扣减/恢复、软删除+权限（原系统超时关闭软删，本项目改为不软删保留记录）
  - order-service 实现订单：OrderStatusEnum（数字枚举+状态机）、Order/OrderItem 实体、OrderMapper/OrderItemMapper(+XML)、OrderService(+Impl)、OrderController（8 接口）
  - ProductMapper 扩展：stock 字段 + deductStock/restoreStock（乐观锁防超卖）
  - OrderServiceApplication 加 @EnableScheduling（超时关闭定时任务）；gateway 补 /api/orders/** 路由
  - 生成 docs/sql/order-status-migrate.sql（order_status 字符串→数字）
  - Docker 部署 MySQL：docker-compose 加 mysql 服务（3307）、mysqldump 迁移 9 张表、4 服务 dev 配置 3306→3307
  - `mvn -pl order-service -am compile` 通过
- 关键决策：
  - 订单状态用数字 TINYINT + 枚举（电商标准做法）
  - 下单/取消用 @Transactional，库存乐观锁防超卖
  - 超时关闭用 @Scheduled + 二次确认防并发
- 创建/修改的文件：
  - 新增：enums/OrderStatusEnum、entity/Order+OrderItem、mapper/OrderMapper+OrderItemMapper(+XML)、dto/CreateOrderDTO+OrderItemDTO、vo/OrderVO+OrderItemVO、service/OrderService+impl、controller/OrderController、docs/sql/order-status-migrate.sql
  - 修改：Product/ProductMapper(+XML)、OrderServiceApplication、gateway application.yml、docker-compose.yml、4 个 application-dev.yml
- 待办/待确认：
  - ~~order-status-migrate.sql 待用户执行（order_status 迁数字）~~ → 已于 2026-10-09 执行完成（见阶段 N）
  - 未启动端到端验证
  - 取消订单自动生成售后单（aftersale）未做
  - 确认收款/发货本质是 admin 操作，后续拆 /api/admin/orders 收紧权限

## 会话：2026-10-09

### 阶段 M：购物车实现盘点（只读）
- **状态：** complete
- 执行的操作：
  - 读 order-service 全部购物车相关代码：`CartController` / `CartService(+Impl)` / `CartMapper(+XML)` / `entity/Cart` / `entity/Product + ProductMapper(+XML)` / 3 个 DTO / `CartVO`
  - 核对 `docs/sql/order.sql` 的 cart 建表（唯一键 + 外键）、`gateway/application.yml` 路由、`OrderServiceImpl`（确认**未引用 cart**）
  - 对照 Node 原版 `server/controllers/cartController.js` + `routes/cartRoutes.js`：确认 5 接口一致，且 Spring 版把「信任前端快照」改为「后端查 products 表」（防改价）
  - 核实文档约定：`docs/openapi.yaml` 已于 2026-10-05 提交 `8228496` 删除，改为 springdoc 注解自动生成（新增 `docs/swagger-addresses.md`）
  - 核实前端：uniapp `api/` 无 cart 模块、`pages/cart/cart.vue` 未调接口
- 创建/修改的文件：
  - `findings.md`（新增「购物车实现盘点（2026-10-09）」一节）
  - `task_plan.md`（阶段 4 补 3 条购物车待办 + 更新状态）
  - `progress.md`（本段）
- 结论（缺口，详见 findings 该节，未改任何代码）：
  - **下单不清购物车**（OrderServiceImpl 未引用 cart）
  - **加购不校验库存/上架**（ProductMapper.findById 无 stock/listing_status 过滤）
  - **未端到端验证** + 前端 cart api 未接
  - 多租户固定模板店（TEMPLATE_SHOP_ID=1）、insurance 字段为历史遗留

### 阶段 N：orders.order_status 字符串→数字迁移（执行）
- **状态：** complete
- 执行的操作：
  - 核对 `docs/sql/order-status-migrate.sql`：映射与 `OrderStatusEnum` 一致、覆盖全部 52 行、无 NULL
  - 修正脚本头执行命令端口（默认 3306 → Docker 3307），补第 4 步自检 SQL
  - **用户执行**（写操作按规范由用户跑）：`mysql -h127.0.0.1 -P3307 ... -D e_commerce_db < docs/sql/order-status-migrate.sql`
  - 自检通过：列类型 `tinyint`，分布 `2→10 / 3→4 / 4→2 / 5→11 / 6→25`，与迁移前英文值一一对应
- 创建/修改的文件：
  - `docs/sql/order-status-migrate.sql`（执行命令端口 + 自检）
  - `findings.md`（「订单状态数字枚举 + 状态机」节补执行结果与遗留）
  - `progress.md`（本段）
- 说明：
  - 只迁 Docker 3307；本机 3306（回退库）保持英文原样
  - 遗留同类项（未迁）：`orders.sign_status`（varchar `signed`/`unsigned`）、`tags.type`（varchar `custom`）

### 阶段 O：user 表软删除字段（后端接入）
- **状态：** complete（编译通过；未启动端到端验证）
- 执行的操作：
  - 用户已在 Navicat 执行 `ALTER TABLE user ADD COLUMN is_deleted TINYINT NOT NULL DEFAULT 0`（连 Docker 3307），实测列已生效、4 行数据均 0
  - `User` 实体加 `isDeleted`（Integer；`select *` + `map-underscore-to-camel-case: true` 自动映射，无需手写 resultMap）
  - `UserServiceImpl`：登录时 `is_deleted=1` → 403「账号已注销」；新增私有 `findActiveUser` / `isDeleted`，`getProfile` / `updateName` / `uploadAvatar` 统一走 `findActiveUser`（已注销 → 401）
  - `UserMapper.xml` 加注释说明 `findByOpenid` **故意不**过滤 `is_deleted`（注销用户 openid 仍占 uk_openid，过滤会导致重登时 insert 撞唯一键）
- 创建/修改的文件：
  - `User.java`、`UserMapper.xml`、`UserServiceImpl.java`
  - `progress.md`（本段）
- 说明：
  - 只加了字段和读取判断，**未做注销接口**（没有地方把 `is_deleted` 置 1）——留待后续
  - 本机 3306 的 user 表无此列；`select *` 不报错，只是永远判为未注销（回退库场景可接受）

### 阶段 P：注销接口（user.is_deleted=1 + 清购物车，同事务）
- **状态：** complete（编译通过；未启动端到端验证）
- 执行的操作：
  - 新增 `POST /api/user/deactivate`（需登录），`UserServiceImpl.deactivate` 用 `@Transactional` 包住：校验用户 → `softDelete`（is_deleted=1）→ `cartMapper.deleteByUser`（清 cart）
  - user-service 新增 `mapper/CartMapper`（+ `mappers/user/CartMapper.xml`）：跨表直连同库删 cart，为的是与置位共用一个事务（跨服务调用无原子性，Seata 未引入；沿用 order-service 直查 products 的先例）
  - `UserMapper` 加 `softDelete`；`UserService` 加 `deactivate`
- 创建/修改的文件：
  - 新增：`user-service/.../mapper/CartMapper.java`、`resources/mappers/user/CartMapper.xml`
  - 修改：`UserMapper.java`+`UserMapper.xml`、`UserService.java`、`UserServiceImpl.java`、`UserController.java`
  - `findings.md`（「用户软删除」节补注销接口）、`progress.md`（本段）
- 说明：
  - 订单（39 条）与收货地址保留；网关无需改（`/api/user/deactivate` 落到 `/api/**` → user-service）
  - 无 admin 侧注销接口；`consignee_address` 未清

## 会话：2026-10-10

### 阶段 Q：头像静态资源经网关打通（gateway 路由 + 绝对 URL 修正）
- **状态：** in_progress（编译通过；`/upload/**` 路由实测 200；用户确认头像能显示，但**复查发现 URL 仍指向实例地址 `192.168.92.1:9006`**，真机不可达——已改为网关 `PreserveHostHeader` + Host 优先，待重启复测）
- 执行的操作：
  - 排查「小程序点加购 + 取头像」需要哪些模块，结论：MySQL（Docker 3307）+ Nacos 8848 + gateway 8080 + user-service 9006 + order-service 9004；product-service 9003 仅浏览商品时需要（加购由 order-service 同库查 `products`），Redis 不需要
  - gateway 补路由 `/upload/**` → `lb://user-service`（与 `/image/**` 对称）：原先缺失，头像相对路径经网关必然 404
  - `fullUrl` 改用 `X-Forwarded-Host` / `X-Forwarded-Proto`：网关默认把 Host 改写成下游地址，原实现拼出的头像 URL 指向 `127.0.0.1:9006`（开发者工具本机碰巧可见，真机不可达）；外链 avatar 原样返回，不再二次拼域名；抽到 `common/utils/UrlUtil.java` 供两个 controller 共用
  - **修集 `Failed to load local image resource /upload/avatar/...`**：根因是 `/api/wxlogin` 直接返回库里相对路径（`/api/user/profile` 才拼域名），前端 `ensureLogin` 把它写进 store 给 `<image>` 用，小程序当成包内路径；`WxLoginController` 同样拼一次完整 URL（那个 500 是开发者工具本地静态服务报的，实测该文件经网关 200）
  - **复查（自签 HS512 令牌实测 `/api/user/profile`）**：经网关返回 `http://192.168.92.1:9006/...`（lb 解析出的实例地址）。读 gateway 5.0.3 源码确认 `XForwardedHeadersFilter` 取的是 `request.getURI().getHost()`，所以 `X-Forwarded-Host` 恒为下游实例地址、客户端自带值也会被覆盖 → 改为网关给 `/api/**` 路由加 `PreserveHostHeader`，`UrlUtil` 取值顺序改为 Host 优先、`X-Forwarded-Host` 仅兜底
- 创建/修改的文件：
  - 新增 `common/src/main/java/ecommerce/utils/UrlUtil.java`
  - 修改 `gateway/src/main/resources/application.yml`、`user-service/.../controller/UserController.java`、`WxLoginController.java`
  - `findings.md`（新增「头像静态资源路由 + 绝对 URL 拼接」节）、`task_plan.md`（「已做决策」补一条）、`progress.md`（本段）
- 说明：
  - 验证命令见 `findings.md` 对应节；gateway 改路由需重启，user-service 改代码需重启（均无热加载）

### 阶段 R：C 端免登录 + 手机号登录后端接口
- **状态：** 完成（后端编译通过；前端 C 端免登录落地，手机号接口前端未接入、保留备用）
- 背景：小程序 C 端登录链路一直不顺——头像「退出登录后不显示」排查发现 `upload/avatar/` 目录空、`user` 表 avatar 恒 NULL、账号 id 12→13 被重建，即头像从未成功落库；手机号登录又要真机 + 代码包超 4MB（80051）。用户拍板：**小程序 C 端前后端免登录，电商后台管理保持登录**。
- 执行的操作：
  - 手机号登录后端（用户小程序已企业认证，但最终前端未接入）：新增 `PhoneDTO`、`UserService.bindPhone`、`POST /api/user/phone`；`HttpClientUtil` 加 `doPostJson`；`UserServiceImpl` 加 `getAccessToken`（内存缓存 2 小时、提前 5 分钟刷新）调微信 `getuserphonenumber` 换手机号写 `user.phone`；`UserMapper.updatePhone`
  - C 端免登录（后端无改动）：商品浏览本就在 `EXCLUDE_AUTH_PATHS` 免登录，加购/下单靠前端启动时静默登录自动带的 token 定位 userId
- 创建/修改的文件：
  - 新增 `user-service/.../dto/PhoneDTO.java`
  - 修改 `common/.../HttpClientUtil.java`（加 doPostJson）、`user-service/.../UserMapper.java`、`UserMapper.xml`、`UserService.java`、`UserServiceImpl.java`、`UserController.java`
- 说明：
  - `/api/user/phone` 走网关 `/api/**` 兜底路由，无需改网关
  - `getPhoneNumber` 必须真机（开发者工具模拟 code 换不到真实手机号）；接口已实现但前端未接入，保留备用

## 测试结果

| 测试 | 输入 | 预期结果 | 实际结果 | 状态 |
|------|------|---------|---------|------|
| 编译 | `mvn -B clean compile` | 编译通过 | **通过**（10 模块全部 BUILD SUCCESS，user-service 11 源文件） | 通过 |
| 单元测试 | `mvnw test` | — | **未执行** | 未运行 |
| `TempTimeoutVerify.testReadTimeoutTakesEffect` | 本地假服务 10s 不回包 | 5s 读超时后失败 | **未执行**（该测试需真实起 Spring 上下文 + 本地端口） | 未运行 |
| 编译（common + user-service） | `./mvnw -pl common,user-service -am compile` | 编译通过 | **通过**（无输出即成功） | 通过 |
| 头像静态资源经网关 | `curl -i http://127.0.0.1:8080/upload/avatar/9_1791625414298.jpg` | HTTP 200 | **通过**（经网关 200、直连 9006 也 200） | 通过 |
| avatar 指向客户端域名 | 自签 HS512 令牌 `GET /api/user/profile` 经网关 8080 | `http://127.0.0.1:8080/upload/avatar/...` | **未通过**：实测 `http://192.168.92.1:9006/...`（lb 实例地址）；`X-Forwarded-Host` 被网关覆盖不可用 → 改用网关 `PreserveHostHeader` + Host 优先，**待重启复测** | 未通过 |
| 前端头像显示 | 开发者工具选头像 → 我的页/加购弹窗 | 头像正常显示、无渲染层报错 | **通过**（用户确认显示正常；但 URL 是 `192.168.92.1:9006`，本机可达掩盖了真机问题） | 通过 |

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
| 我在哪里？ | 阶段 4/6：头像链路已修三处（网关补 `/upload/**` 路由、`/api/wxlogin` 也拼完整 URL、`UrlUtil` 抽到 common）；**复查发现 URL 仍指向实例地址**，已改用网关 `PreserveHostHeader` + Host 优先，待重启复测 |
| 我要去哪里？ | 重启 gateway + user-service 复测头像 URL 为 8080；补购物车缺口（下单清购物车、加购校验库存上架）；取消订单关联 aftersale 售后单 |
| 目标是什么？ | 拆成真·微服务部署，跑通 Nacos 注册 + Gateway 路由，再逐个补业务服务 |
| 我学到了什么？ | 见 findings.md「头像静态资源路由 + 绝对 URL 拼接（2026-10-10）」「购物车实现盘点（2026-10-09）」与「订单域迁移 + 购物车后端」；静态资源地址要统一拼、别各拼各的 |
| 我做了什么？ | 阶段 A–Q：读 Node 后端、搭 8 服务骨架、auth 登录、商品域迁移、AI Agent、Redis 分类缓存、订单域迁移 + 购物车、订单功能 + Docker MySQL、用户软删除 + 注销接口、修头像静态资源链路 |

---
*每个阶段完成后或遇到错误时更新此文件*
