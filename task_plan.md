# 任务计划：摸清 e-commerce-boot 现状并固化后续路线

将此文件作为任务的持久化路线图。在开始复杂工作前创建，并在阶段变化时及时更新。

## 目标

摸清 e-commerce-boot 后端（架构 / 核心链路 / 遗留问题）的现状，并把结论与后续开发顺序固化到 `task_plan.md` / `findings.md` / `progress.md` 三个文件里。

## 下一步

把猜测升级为事实：向用户确认三个待定问题（暂存区 `UserController` 为何被清空、工作区注释回退是否有意、下一步动哪个模块），再进入阶段 5。

## 当前阶段

阶段 5：后续开发（pending，等用户定优先级）

## 各阶段

### 阶段 1：项目结构与配置梳理
- [x] 列出源码 / 资源 / 文档全貌（22 个 Java 文件、1 份 openapi.yaml）
- [x] 读 pom.xml，确认技术栈与版本
- [x] 读 application.yml，确认配置项与「凭据不进版本库」的做法
- [x] 记录模块划分与目录结构约定
- **状态：** complete

### 阶段 2：核心链路梳理
- [x] 小程序登录链路：`/api/wxlogin` → code 换 openid → 首次登录自动注册 → 签 JWT
- [x] 鉴权链路：`JwtInterceptor` 取头 → 校验签名 → 比对 `type` → 写 `UserContext`
- [x] 用户资料链路：`/api/user/profile` → 查库 → 组装 VO → 拼完整头像 URL
- [x] 头像上传链路：白名单校验 → 落盘 → 写库相对路径 → 删旧文件
- **状态：** complete

### 阶段 3：遗留问题与不一致盘点
- [x] 对照 `project_spec.md`（2026-09-28 已并入 `findings.md`「项目架构约定」）捡出未落地项（上传目录硬编码、PC 登录入口、后台 admin 模块）
- [x] 核对代码与 `docs/openapi.yaml` 的一致性，找出文档缺口
- [x] 检查未提交改动与暂存区状态
- [x] 记录已决策不修的项，避免重复讨论
- **状态：** complete

### 阶段 4：生成 planning 文件
- [x] 写 `findings.md`（研究发现 + 遗留风险）
- [x] 写 `progress.md`（本次会话日志）
- [x] 写 `task_plan.md`（本文件）
- [x] 按用户指示把根目录 `project_spec.md` 全文并入 `findings.md`，并删除原文件
- **状态：** complete

### 阶段 5：后续开发（待用户定优先级）
- [ ] 待定：`user` 模块遗留项收尾（上传目录配置化 → 见 findings）
- [ ] 待定：PC 端商城登录入口 `PcLoginController`
- [ ] 待定：后台 `admin` 模块（表 + RBAC + `AdminLoginController`）
- [ ] 待定：业务模块 `goods` / `order` / `aftersale`
- **状态：** pending

## 关键问题

1. **暂存区里的 `UserController` 为什么是「被清空」的版本？** `git diff --cached` 显示暂存区比 HEAD 少 58 行（类基本为空），而工作区又是完整类。此时直接 `git commit` 会先提交一个丢接口的版本。是误操作还是有意分两步提交？
2. **工作区把 `UserController` / `WebMvcConfig` 的注释和封装质量往回退了，是有意的吗？** HEAD 版本有类注释、显式 import、`private fullUrl`、以及 `consumes` 的说明注释；工作区换成了通配符 import、`public fullUrl`、`//接口前缀`、一段「这是java的多行注释」的占位注释。
3. 头像上传只用 Content-Type 白名单，挡不住伪装文件，**上线前是否需要补 magic number 校验**？
4. 前端三端（小程序 / PC 商城 / 后台）不在本仓库，本仓库的契约改动如何同步给对方？只靠 `docs/openapi.yaml` 是否够？

## 已做决策

| 决策 | 理由 |
|------|------|
| 按业务模块建包，不按端建包（`user` / `goods` / `order` / `aftersale`） | 同一份业务数据多端共用，按端建包会复制粘贴；见 `findings.md`「项目架构约定」 |
| C 端统一 `User*` 前缀（小程序与 PC 商城共用一套接口），后台 `Admin*` | 两者业务逻辑一致，差异只在登录入口与 VO 字段裁剪 |
| 失败响应返回真实 HTTP 状态码（400/401/403/404/413/500），不恒 200 | 前端可用 HTTP 语义直接判断；`Result{success,data,message,code}` 只作补充 |
| 公共失败响应由 `OpenApiConfig` 全局注入，Controller 上不写 `@ApiResponse` | 400/401/403/413/500 都出自全局处理器，逐接口写会重复几百遍 |
| 免登录清单只有 `WebMvcConfig.EXCLUDE_AUTH_PATHS` 一份 | 拦截器豁免与文档补全读同一份，避免两处漂移 |
| 令牌密钥 / 数据库凭据 / 微信密钥不写进版本库 | `username`/`password`/`jwt.secret` 故意不给默认值，缺失即启动失败 |

## 遇到的错误

| 错误 | 尝试次数 | 解决方案 |
|------|---------|---------|
| `git diff` 显示的 `UserController` 内容与 `git status` 的 `MM` 状态对不上，看不出真实改动 | 1 | 改用 `git diff --cached` 与 `git diff HEAD` 分别比，确认暂存区与工作区各有一份不同改动 |

## 备注
- 随着工作推进，将阶段状态从 `pending` 更新为 `in_progress`，再更新为 `complete`。
- 做重大决策前，重新读取目标和下一步。
- 及时记录错误，避免重复失败的方法。
- 阶段 5 的待办来自 `findings.md`「项目架构约定」与代码注释，**未经用户确认优先级**，不要自行开工。
- 架构约定已集中到 `findings.md`「项目架构约定」一节（原根目录 `project_spec.md`，2026-09-28 并入后删除原文件）。
