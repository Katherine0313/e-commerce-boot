# 开发者指南

## 项目目的

e-commerce-boot 是电商微服务父工程。它为小程序/PC 提供交易 API，为管理端提供运营与系统管理 API。

**核心职责**:
- 经网关统一暴露 REST 与部分 WebSocket
- 各服务独立部署、Nacos 发现、共享 `common`
- 本地与生产凭据走环境变量，不入库

**相关系统**:
- 微信小程序前端 — 经 `localhost:8080` 或生产 Nginx
- 管理后台前端 — 同一网关，`/api/admin/**` 与 `/api/login`
- `ecommerce-python` — 导购机器人，网关 `/api/agent`、`/api/mall/ai`、`/api/admin/ai`

## 环境搭建

### 前置条件

- JDK 17+
- Maven（仓库含 `mvnw`）
- Nacos（本地 `docker compose up -d nacos`，8848）
- MySQL 8（文档同时提到本机 3306 与 Compose 映射 3307，以当前 `application-dev.yml` 为准）
- Redis（order 幂等与管理员会话）
- 可选：RabbitMQ、Python AI

### 安装

```bash
git clone <repo-url>
cd e-commerce-boot
docker compose up -d nacos
# 按需启动 mysql / redis
```

各服务默认 `spring.profiles.active=dev`。`application-dev.yml` 被 gitignore，需在本机按 `docs/env.md` 自行准备（**不要把密钥提交进 Wiki 或 Git**）。

### 环境变量

| 变量 | 必需 | 描述 | 示例 |
|------|------|------|------|
| `DB_URL` | 是 | JDBC URL | `jdbc:mysql://localhost:3306/e_commerce_db?...` |
| `DB_USERNAME` | 是 | 数据库用户 | `<DB_USER>` |
| `DB_PASSWORD` | 是 | 数据库密码 | `<DB_PASSWORD>` |
| `JWT_SECRET` | 是 | 全模块一致的签名密钥 | `<JWT_SECRET>` |
| `WECHAT_APPID` | user-service | 小程序 AppId | `<WECHAT_APPID>` |
| `WECHAT_SECRET` | user-service | 小程序 Secret | `<WECHAT_SECRET>` |
| `NACOS_ADDR` | 否 | 默认 `127.0.0.1:8848` | |
| `SPRINGDOC_ENABLED` | 否 | 下游 Swagger，默认 false | `true` |
| `AI_SERVICE_URL` | 否 | 网关 AI 上游 | `http://127.0.0.1:8000` |
| `SQL_DEBUG` | 否 | MyBatis SQL 日志 | `DEBUG` |

`JWT_SECRET` 以**系统环境变量优先于 yml**。两边不一致会导致全站 401。

### 运行

IDEA 分别启动：gateway、auth、product、order、aftersale、user、system（及可选 file）。  
命令行示例：`mvn -pl gateway -am spring-boot:run`。

生产打包：`./mvnw clean package -DskipTests`，步骤见 `deploy/README.md`。

Swagger 聚合：`http://localhost:8080/swagger-ui/index.html`（下游需 `SPRINGDOC_ENABLED=true`）。

## 开发工作流

### 代码质量工具

仓库未统一配置 ESLint/Prettier。以模块内 Java 风格与现有注释习惯为准。测试目录在本次梳理中**未发现完整自动化测试套件**（无法确定 CI 门禁）。

### 提交前检查

1. 不提交 `application-dev.yml`、密钥、真实 JWT
2. 网关 Path 与 Controller `@RequestMapping` 保持一致
3. 管理接口加 `@RequirePerm`（与现有后台接口一致）

### 分支策略

当前仓库主分支为 `master`。无法从代码确定强制 PR 流程。

## 常见任务

### 添加新 API 端点

1. 在对应 `*-service` 增加 Controller + Service + Mapper
2. 若路径不属于现有网关谓词，更新 `gateway/.../application.yml`（更具体规则放在 `/api/**` 兜底之前）
3. 需登录：走 `JwtInterceptor`；管理端加权限注解
4. 用 springdoc 注解，勿手写 openapi.yaml（已废弃）

### 添加内部 AI 接口

路径 `/internal/ai/**`，由 `InternalApiInterceptor` 校验 Token。网关已按服务拆分 product/order/aftersale。

### 添加环境变量

1. `application.yml` 使用 `${NAME}`，不写真实值
2. 本地只放 gitignore 的 `application-dev.yml`
3. 更新 `docs/env.md`（运维清单）与本指南占位表

### 数据库

禁止执行数据库重置。迁移以现有 SQL/手工变更为准；`system-service` 提供库表查看与备份接口，不是 Flyway 目录。

## 编码规范

### 文件组织

- 包名 `ecommerce.<服务>`：`controller` / `service` / `mapper` / `dto` / `vo`
- Mapper XML：`src/main/resources/mappers/`

### 命名

| 类型 | 约定 | 示例 |
|------|------|------|
| 服务模块 | kebab + `-service` | `order-service` |
| 类 | PascalCase | `OrderController` |
| 管理端路径 | `/api/admin/...` | `/api/admin/orders` |
| C 端路径 | `/api/...` | `/api/orders` |

### 错误处理

抛 `BusinessException`，由 `GlobalExceptionHandler` 转为 `Result` 与对应 HTTP 状态。统一结构见 `ecommerce.common.Result`。

### 日志

操作日志：`@OperationLog` + `OperationLogAspect` 异步写入。IP 脱敏用 `IpMasker`。

### 测试

无法确定统一测试约定；新增逻辑优先贴近现有 Service 分层，勿引入无关重构。
