# 本地开发环境变量清单

> 2026-10-17 起，各服务已删除 `application-dev.yml`，所有凭据统一走环境变量。
> 本地启动前必须在 IDEA 的 Run Configuration（或系统环境变量）里配好下列变量，
> 否则 `JwtProperties` / `WeChatProperties` 启动校验非空会直接失败。

## 必填（缺失会导致启动失败）

| 变量 | 值 | 用途 |
|------|-----|------|
| `DB_URL` | `jdbc:mysql://localhost:3307/e_commerce_db?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&useUnicode=true` | 数据库连接（Docker MySQL 3307） |
| `DB_USERNAME` | `root` | 数据库用户名 |
| `DB_PASSWORD` | `123456` | 数据库密码 |
| `JWT_SECRET` | `pIc70vBVlHlDDXb6Bfd9EBLHmAPL6JyCUieDSNbZaLlpIJ1T/rlEifXzZCwNTH8m` | JWT 签名密钥（8 个模块必须一致） |
| `WECHAT_APPID` | `wxa18a22c8c5c1fa12` | 微信小程序 appid（user-service 登录） |
| `WECHAT_SECRET` | `0df36c81d948e6083079462fdbd762f2` | 微信小程序 secret |
| `SILICONFLOW_API_KEY` | `sk-...`（dev.yml 里的值含 xxx，需核对真实 key） | 硅基流动 AI 导购 key（ai-agent-service） |

## 可选（本地开发便利）

| 变量 | 说明 |
|------|------|
| `SPRINGDOC_ENABLED` | 设 `true` 开启 Swagger 文档（默认关闭） |
| `SQL_DEBUG` | 设 `DEBUG` 打印 MyBatis SQL（order/product/aftersale，默认 INFO 不打印） |

## 说明

- 之前这些值散落在各模块的 `application-dev.yml`（已删除）。现在主配置 `application.yml` 里
  统一用 `${环境变量}` 占位，凭据只有一个来源。
- `application.yml` 中 DB 的默认值是 3306（本机 MySQL80）；本地开发连 Docker 的 3307，
  所以 `DB_URL` 必须显式指定 3307。
- 用户名/密码故意不给默认值，缺失就启动失败，避免悄悄用了错误的值。
