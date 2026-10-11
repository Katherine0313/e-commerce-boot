# AI 评审说明：e-commerce-boot

**基线**：评审 working tree（69 文件未提交，购物车/注销/UrlUtil 都在工作区），git log 无意义。单人学习项目，Node 单体 → Spring Cloud 迁移中，小程序先行。
**栈**：Boot 4.1.0 / Java 17 / Cloud 2025.1.3 / Alibaba 2025.1.0.0 / Nacos 3.1.1 / MySQL(3307) / MyBatis 4.0.1 / jjwt 0.13 / springdoc 3.1.1 / **Jackson 3（`tools.jackson`）** —— 勿按 Boot 3/Jackson 2 常识判错。
**风格**：中文注释、构造器注入、Lombok、MyBatis XML、统一 `Result<T>`。

**模块**（端口/文件数）：gateway 8080/1 路由无业务 · user 9006/12 登录资料头像注销 · order 9004/24 购物车+订单 · product 9003/18 商品分类标签推荐 · auth 9001/9 后台登录（admin 表未建，不可用）· ai-agent 9008/17 对话+SSE · shop 9002 / aftersale 9005 / file 9007 均**空壳** · common 13 框架层。
链路：小程序 → gateway（唯一入口）→ `lb://` → 下游 JwtInterceptor → 共享库 `e_commerce_db`。

**约定**（判定「不一致」的基准）
- 包按业务模块，模块内 `controller/service/mapper/entity/dto/vo`。
- JWT `type`=`user`/`admin`；`/api/admin/**` 要 admin，其余 user；免登录白名单唯一来源 `WebMvcConfig.EXCLUDE_AUTH_PATHS`。
- `Result{success,data,message,code}` + HTTP 状态码真实返回；公共失败码由 `OpenApiConfig` 全局注入，Controller 不写 `@ApiResponse`。
- `/image/**`→product、`/upload/**`→user（均免登录）；绝对 URL 由后端 `UrlUtil.fullUrl` 拼，网关对该路由配了 `PreserveHostHeader`（网关自带 `X-Forwarded-Host` 不可信）。
- 共享库共享表 + `shop_id`（固定 1）；新表显式 `COLLATE utf8mb4_general_ci`；状态字段 TINYINT+枚举；时间字段商品域 `create_time/update_time`、订单域 `created_at/updated_at`；Redis 只做分类树缓存。

**表**（9）：`user`(软删、uk_openid) · `products` · `categories` · `tags` · `cart`(快照价格) · `orders`(order_status 已 TINYINT、user_id RESTRICT) · `order_items`(快照，无外键) · `consignee_address` · `shops`。`admin` 未建。
**接口**：user `POST /api/wxlogin`(免登录)、`/api/user/{profile,avatar,name,deactivate}`(需登录) · order `/api/cart`、`/api/orders` + `/{id}/pay|confirm|ship|finish|cancel`(需登录) · product `/api/{products,categories,tags,products/recommend}`(免登录) · ai-agent `/api/agent/chat(/stream)`(免登录) · auth `/api/{login,logout,user/info}`。

**已决策「暂不修」——勿重复报**：① `JwtInterceptor.afterCompletion` 异常路径不清 `UserContext` ② `openid` 排序规则大小写不敏感 ③ `CartMapper.update` 不带 `user_id` ④ DB 凭据曾入 git 历史 ⑤ CORS 放开所有来源+credentials ⑥ 上传目录硬编码 ⑦ 上传只校验 Content-Type ⑧ 3306 库已与 3307 分叉 ⑨ 跨服务同库直连（有意为之，换同事务）。

**未验证/待办**：**无任何单元测试**；头像 URL 经网关是否返回客户端域名（待重启复测）；下单不清购物车、加购不校验库存上架、取消订单不关联售后；后台建表+RBAC；三个空壳服务；沙箱回收；https 协议透传。

## 评审重点

鉴权与越权（路径分流、白名单过宽、UserContext 生命周期）· 事务一致性（同库直连边界、软删除不触发 CASCADE）· 金额与并发（快照价格、重复提交、超卖、状态机绕过）· 上传安全（类型伪造、路径、静态直出）· Host 注入与 `PreserveHostHeader` 影响面 · MyBatis 陷阱（`select *`、`foreach in`、Lombok `isDeleted` 用 Integer）· 缓存失效 · Boot 4/Jackson 3 新行为 · 异常与 `Result` 一致性 · `/api/**` 全兜底给 user-service 的临时路由。

**复现**：`docker compose up -d`（nacos 8848、mysql 3307）→ 设 `JWT_SECRET`（user/order 必须同值）→ 起 gateway/user/order/product/ai-agent 五个启动类。`/api/categories` 依赖 Redis，不起会 500。
**文档**：`findings.md`（事实主源）/ `progress.md` / `task_plan.md`。过时点：`docs/openapi.yaml` 已删（改注解自动生成）；findings「项目架构约定」部分示例仍是旧的单模块包结构。
