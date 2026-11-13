# 杰米多多 · 电商系统（后端）

单店 B2C 电商的 Java 后端微服务。配套 4 个前端 / AI 仓库，构成完整的「小程序 + PC 商城 + 后台管理 + AI 客服」体系。

## 技术栈

| | |
|---|---|
| 语言 / 框架 | Java 17 · Spring Boot 4.1.0 · Spring Cloud 2025.1.3 · Spring Cloud Alibaba 2025.1.0.0 |
| 注册配置 | Nacos（服务发现 + 配置中心） |
| 网关 | Spring Cloud Gateway（WebFlux） |
| 服务间调用 | OpenFeign（少量）+ 同库直查为主 |
| 持久层 | MyBatis + XML（MyBatis-Plus） |
| 数据库 | MySQL（生产 5.7 / 本地 8.0，`utf8mb4_general_ci`） |
| 缓存 | Redis（分类树缓存、下单幂等、在线会话、令牌失效） |
| 鉴权 | JWT（`type=user` / `type=admin` 区分两端）+ Redis 令牌失效标记 |
| 接口文档 | springdoc-openapi（默认关闭，开发期开） |

## 服务拆分

| 模块 | 职责 |
|---|---|
| `gateway` | 统一入口、路由、跨域、静态资源代理 |
| `auth-service` | 后台账号密码登录、图形验证码、失败锁定、RBAC 权限签发 |
| `user-service` | C 端用户（小程序 openid 登录 / PC 账号密码登录）、会员、地址、购物车、收藏 |
| `product-service` | 商品、分类、规格模板、评价、库存预警、上传图片 |
| `order-service` | 订单、支付（模拟）、配送包裹、优惠券、售后入口 |
| `aftersale-service` | 售后单：仅退款 / 退货退款 / 换货补发 |
| `system-service` | 后台系统管理、系统监控、数据库管理、操作审计 |
| `file-service` | 文件上传下载（骨架） |
| `common` | 下沉框架层：JWT 拦截器、内部接口鉴权、操作日志、异常处理 |

## 仓库清单

| 仓库 | 内容 |
|---|---|
| **[e-commerce-boot](https://github.com/Katherine0313/e-commerce-boot.git)** | **Java 后端微服务（本仓库，核心）** |
| [e-commerce](https://github.com/Katherine0313/e-commerce.git) | 后台管理系统（Vue 3 + Ant Design Vue） |
| [ecommerce-mall](https://github.com/Katherine0313/ecommerce-mall.git) | PC 商城前端（Vue 3 + Vite） |
| [e-commerce-uniapp](https://github.com/Katherine0313/e-commerce-uniapp.git) | 微信小程序（uni-app） |
| [ecommerce-python](https://github.com/Katherine0313/ecommerce-python.git) | AI 客服服务（Python FastAPI + LangGraph + 知识库检索） |

本仓库是整套系统的核心：另外 4 个仓库（后台、商城、小程序、AI 客服）都通过 REST 调它。

## 目录结构

```
e-commerce-boot/
├── common/                下沉框架层（JWT、鉴权、日志、异常）
├── gateway/               网关
├── auth-service/          后台登录与权限
├── user-service/          C 端用户
├── product-service/       商品与库存
├── order-service/         订单与配送
├── aftersale-service/     售后
├── system-service/        系统管理与监控
├── file-service/          文件服务
├── deploy/                生产部署（compose / 发布脚本 / Nginx / 备份脚本）
├── docs/
│   ├── sql/               数据库变更脚本（见 docs/sql/README.md）
│   ├── planning/          规划三件套：findings / task_plan / progress
│   └── images/            README 用图
├── sql/e_commerce_db.sql  脱敏后的完整数据库快照（demo 数据）
├── tools/                 本地运维与维护脚本
└── pom.xml                父工程
```

## 演示地址

<p align="center">
  <img src="docs/images/miniapp-qrcode.png" width="240" alt="杰米多多小程序码">
</p>

| 端 | 地址 |
|---|---|
| 小程序 | 杰米多多（微信搜小程序码） |
| 后台管理 | https://www.jiemids.com |
| PC 商城 | https://www.xiaojiecpn.com |
| 联系 | 3100326817@qq.com |

## 本地开发

1. 起 Nacos：`cd deploy && docker compose up -d nacos`
2. 各服务准备 `application-dev.yml`（已 gitignore，凭据清单见 `docs/planning/findings.md`）
3. 启动顺序：`gateway` → 业务服务 → `auth-service` 最后（auth 停止签发旧 claim 后旧服务才会接受新 token）

数据库变更脚本统一放 `docs/sql/`，**本地执行完必须在生产执行同一份**，详见 `docs/sql/README.md`。

生产发布用 `./deploy/release.sh <服务名>`，一键构建镜像 + 推送 + SSH 部署 + 回滚，详见 `deploy/README.md`。

## Demo 数据

`sql/e_commerce_db.sql` 是脱敏后的完整数据库快照（63 张表，含演示商品 / 订单 / 评价 / 知识库），clone 下来就能导入跑起来：

```bash
mysql -u<用户> -p -D e_commerce_db --default-character-set=utf8mb4 < sql/e_commerce_db.sql
```

## 文档索引

| 文档 | 内容 |
|---|---|
| [`docs/planning/findings.md`](docs/planning/findings.md) | 事实与结论：架构、数据流、踩过的坑、约定 |
| [`docs/planning/task_plan.md`](docs/planning/task_plan.md) | 持久化路线图与阶段验收标准 |
| [`docs/planning/progress.md`](docs/planning/progress.md) | 当前进度快照与执行记录 |
| [`docs/sql/README.md`](docs/sql/README.md) | 数据库脚本分类、执行方式、幂等要求 |
| [`deploy/README.md`](deploy/README.md) | 生产部署手册 |