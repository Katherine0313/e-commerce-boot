# 后端架构约定

> 微信小程序、PC 端商城（网页版）、PC 端后台管理系统（Vue）三个前端共用一个后端（本项目）。本文记录模块划分与命名规则，后续开发照此执行。

## 核心原则

按 **业务模块** 建包，不按「端」建包。每个业务模块内部「数据共用、接口分层」。

- 不建 `wx`（小程序端）大包 + `pc`（PC 端商城）大包 + `admin`（后台）大包，而是 `goods` / `order` / `aftersale` 这种业务包。
- 同一份业务数据多端共用，只是接口、权限、可见字段不同。

## 术语约定

- **member 统一叫「用户」，不叫「会员」**。代码注释、接口描述（`@Tag` / `@Operation` / `@Schema`）、返回给前端的提示文案，一律写「用户」。
  - 例：`MemberController` / `MemberService` / `MemberProfileVO` 的注释、错误提示「用户不存在」。
  - 后台身份叫「管理员 admin」，与「用户 member」对应，不混淆。

## 三端划分

| 端 | 身份 | JWT type | 路径前缀 | 说明 |
|---|---|---|---|---|
| 顾客端 C（小程序） | 会员 member | `member` | `/api/**` | wx.login 换 openid 登录 |
| 顾客端 C（PC 端商城） | 会员 member | `member` | `/api/**` | 账号密码 / 验证码登录 |
| 后台 B（PC 端管理系统） | 管理员 admin | `admin` | `/api/admin/**` | 账号密码登录 |

小程序与 PC 端商城 **共用同一个会员账号体系**（同一张 `member` 表、同一套 C 端业务接口），只是登录方式不同。真正区分端的地方只有两处：**登录入口** 和 **前端 UI**，后端业务逻辑是同一套。

## 目录结构

```
ecommerce
├── common/  configuration/  exception/  interceptor/  properties/  utils/
│   （框架层，多端共用）
│
├── member        # 会员/顾客（小程序 + PC 端商城共用）
│   ├── controller/  WxLoginController  /api/wxlogin   （小程序登录）
│   │                PcLoginController  /api/login     （PC 端商城登录，预留）
│   ├── service/     MemberService（含各登录方式 + 会员数据操作）
│   ├── mapper/      MemberMapper
│   ├── entity/      Member
│   └── dto/  vo/    WxLoginDTO / WxLoginVO 等
│
├── admin         # 管理员 / 角色 / 权限（只有后台，RBAC 单独建表，与 member 分开）
│
├── goods         # 商品 —— 两端都有（C 端 + 后台）
│   ├── controller/  MemberGoodsController   /api/goods/**
│   │                AdminGoodsController    /api/admin/goods/**
│   ├── service/     MemberGoodsService、AdminGoodsService
│   ├── mapper/      GoodsMapper             ← 共用
│   ├── entity/      Goods                   ← 共用
│   └── dto/  vo/    按端拆（Member... / Admin...）
│
├── order         # 订单 —— 两端都有，同上
│
└── aftersale     # 售后 —— 两端都有，同上
```

## 命名与路径规则

- **C 端（顾客）= `Member` 前缀**：`MemberGoodsController`、`MemberOrderController`。小程序与 PC 端商城共用这套接口。
- **登录入口 = 按渠道前缀**：`WxLoginController`（小程序）、`PcLoginController`（PC 端商城，预留），因为它们本身就是「端」的差异所在。
- **后台 = `Admin` 前缀**：`AdminGoodsController`、`AdminOrderController`
- **路径**：C 端 `/api/**`，后台 `/api/admin/**`
- **身份类型**：JWT 里 `type` 常量 `member` / `admin`，由 `JwtInterceptor` 按路径分流校验（`/api/admin/**` → admin，其余 → member）

## 各层拆分规则

| 层 | 是否按端拆 | 说明 |
| --- | --- | --- |
| entity | 共用 | 多端操作同一张表，只写一份 |
| mapper | 共用 | 同上 |
| service | 按「C 端 / 后台」拆 | C 端（小程序 + PC 端商城）共用一份 Member*Service，后台 Admin*Service 流程不同 |
| controller | 按「C 端 / 后台」拆 | `Member*` / `Admin*` 前缀区分；登录入口再按渠道 `Wx` / `Pc` 分 |
| dto / vo | 按「C 端 / 后台」拆 | 可见 / 可写字段不同（后台看成本价、库存，顾客端不该看到） |

> 关键点：C 端不按「小程序 / PC 端商城」拆，因为两者的业务逻辑完全一样，拆了就是复制粘贴。若某天 PC 端商城确有差异字段，属于 vo 层裁剪，不拆 service。

## 身份模块（例外）

`member`（会员/顾客）与 `admin`（管理员）是两个不同实体、不同表、不同登录方式，各自独立建模块，不共用。

- 会员：`member` 表。小程序 `wx.login()` 换 openid 登录；PC 端商城账号密码 / 验证码登录。同一账号可同时绑定 openid + 手机号。
- 后台管理员：`admin` 表 + RBAC（角色 / 权限单独建表），账号 + 密码登录。

## 迁移落地顺序

贴合分阶段迁移策略：小程序先跑通（`member` + 各业务模块的 `Member*` 部分），PC 端商城接入时补登录入口（`PcLoginController`），后台功能之后逐个模块补 `Admin*` 部分。结构先留好位置，往里填即可。

## 接口文档同步约定

> 后端接口有改动，`docs/openapi.yaml` 必须同步，不能只改代码。

- **同步时机**：新增 / 修改 / 删除 Controller 接口，或 DTO / VO 字段变化时，同步更新 `docs/openapi.yaml`。
- **为什么**：`docs/openapi.yaml` 是给前端看的静态契约文档，不会随代码自动更新，必须手动同步。
- **响应结构**：统一按 `Result<T>` 的实际字段写 —— `success` / `data` / `message` / `code`；HTTP 状态码真实返回（成功 200，失败 400/401/403/404/500），不要沿用旧的 `code`/`msg`、HTTP 恒 200 的写法。
- **运行时文档**：springdoc 自动生成的 `/swagger-ui.html`、`/v3/api-docs` 无需手动维护；`docs/openapi.yaml` 是另一份手工契约，两者都要保持一致。

## 头像上传遗留待办

> 头像上传（`POST /api/member/avatar`）核心闭环已完成：**库存相对路径、返回时拼完整 URL**（换域名不失效）。以下 4 项是遗留改进点，逐项处理。

| # | 待办 | 现状 | 目标改法 |
|---|---|---|---|
| 1 | 图片类型白名单 | `contentType.startsWith("image/")` 会放行 `image/svg+xml`（可内嵌脚本，XSS 风险） | 白名单仅收 `image/png` / `image/jpeg` / `image/gif` / `image/webp` |
| 2 | 上传大小限制 + 413 | 未配 `spring.servlet.multipart`，默认 max-file-size 1MB；`GlobalExceptionHandler` 未接 `MaxUploadSizeExceededException`，超限返回 500 | 配 `max-file-size` / `max-request-size`，超限返回 413 并给出可读提示 |
| 3 | 接口文档同步 | `docs/openapi.yaml` 缺 `POST /api/member/avatar` | 按 `Result<T>` 结构补全该接口 |
| 4 | 上传目录配置化 | `MemberServiceImpl` 硬编码 `upload/avatar/`，与 `WebMvcConfig` 的 `file:upload/` 需手动对齐 | 抽成配置项（如 `app.upload.dir`），两处读同一配置 |
