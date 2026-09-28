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

## 测试结果

| 测试 | 输入 | 预期结果 | 实际结果 | 状态 |
|------|------|---------|---------|------|
| 编译 | `mvnw compile` | 编译通过 | **未执行**（本次为只读探索，未跑构建） | 未运行 |
| 单元测试 | `mvnw test` | — | **未执行** | 未运行 |
| `TempTimeoutVerify.testReadTimeoutTakesEffect` | 本地假服务 10s 不回包 | 5s 读超时后失败 | **未执行**（该测试需真实起 Spring 上下文 + 本地端口） | 未运行 |

> 说明：本次任务只做代码阅读与文档生成，按规范未执行任何数据库操作；上面的测试均未运行，未做任何「验证通过」的断言。

## 错误日志

| 时间戳 | 错误 | 尝试次数 | 解决方案 |
|--------|------|---------|---------|
| 2026-09-28 | `git diff` 的输出与 `git status` 的 `MM` 状态对不上，无法判断哪份改动是真的 | 1 | 改用 `git diff --cached`（HEAD→暂存区）与 `git diff HEAD`（HEAD→工作区）分别比对；确认存在两份叠加的改动，已记录到 `findings.md` 第 8 节 |

## 五问重启检查

| 问题 | 答案 |
|------|------|
| 我在哪里？ | 阶段 4 收尾：三个规划文件已落盘，`project_spec.md` 已并入并删除；阶段 5 待用户定优先级 |
| 我要去哪里？ | 阶段 5：后续开发，需用户先定模块优先级 |
| 目标是什么？ | 摸清 e-commerce-boot 现状并把结论与后续顺序固化到三个规划文件 |
| 我学到了什么？ | 见 `findings.md`（技术栈 Boot 4.1 / Jackson 3、3 个接口、四条链路、9 项遗留、未提交改动是回退；架构约定已集中到「项目架构约定」一节） |
| 我做了什么？ | 见上方阶段 1–4 与追加段；本次未修改任何业务代码，只删除根目录 `project_spec.md`（内容已并入 `findings.md`） |

---
*每个阶段完成后或遇到错误时更新此文件*
