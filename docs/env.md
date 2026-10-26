# 本地开发凭据清单

> 本地开发凭据写在各服务的 `application-dev.yml`（已被 .gitignore 忽略，不进 git），换设备需重新生成。
> 下方表格是完整清单：本地从 `application-dev.yml` 取值，生产部署改用系统环境变量覆盖。
> 缺失时 `JwtProperties` / `WeChatProperties` 启动校验非空会直接失败。

## 必填（缺失会导致启动失败）

| 变量 | 值 | 用途 |
|------|-----|------|
| `DB_URL` | `jdbc:mysql://localhost:3306/e_commerce_db?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&useUnicode=true` | 数据库连接（本机 MySQL80 3306） |
| `DB_USERNAME` | `e_commerce_db` | 数据库用户名 |
| `DB_PASSWORD` | `123456` | 数据库密码 |
| `JWT_SECRET` | `pIc70vBVlHlDDXb6Bfd9EBLHmAPL6JyCUieDSNbZaLlpIJ1T/rlEifXzZCwNTH8m` | JWT 签名密钥（8 个模块必须一致） |
| `WECHAT_APPID` | `wxa18a22c8c5c1fa12` | 微信小程序 appid（user-service 登录） |
| `WECHAT_SECRET` | `0df36c81d948e6083079462fdbd762f2` | 微信小程序 secret |
| `SILICONFLOW_API_KEY` | `sk-...`（dev.yml 里的值含 xxx，需核对真实 key） | 硅基流动 AI 导购 key（Python `recommender-python/.env`） |

## 可选（本地开发便利）

| 变量 | 说明 |
|------|------|
| `SPRINGDOC_ENABLED` | 设 `true` 开启 Swagger 文档（默认关闭） |
| `SQL_DEBUG` | 设 `DEBUG` 打印 MyBatis SQL（order/product/aftersale，默认 INFO 不打印） |

## 说明

- 之前这些值散落在各模块的 `application-dev.yml`（已删除）。现在主配置 `application.yml` 里
  统一用 `${环境变量}` 占位，凭据只有一个来源。
- MySQL 统一用本机 3306（MySQL80），不再用 Docker 部署；`DB_URL` 用默认 3306 即可。
- 用户名/密码故意不给默认值，缺失就启动失败，避免悄悄用了错误的值。
