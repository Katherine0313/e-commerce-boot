# docs/sql —— 数据库变更脚本

生产与本地跑的是**同一套业务数据**，所以这里每个脚本都要在本地和生产各执行一次（见 `docs/planning/findings.md` 的「写库必须在生产同步」规矩）。

## 执行方式

| 环境 | 命令 |
|---|---|
| 本地 | `mysql -D e_commerce_db --default-character-set=utf8mb4 < docs/sql/xxx.sql` |
| 生产 | `bash deploy/run-sql-prod.sh /www/java/sql/xxx.sql`（SQL 用 scp 传到 `/www/java/sql/`，**不在生产机写文件**） |

> ⚠ **必须带库名执行**：脚本里用 `DATABASE()` 判断当前库，没选库会导致存在性判断失效。
> `run-sql-prod.sh` 已经带了库名；手动执行时记得 `-D`。
>
> ⚠ Windows 下用 mysql 客户端导入必须加 `--default-character-set=utf8mb4`，否则中文乱码。

## 文件命名

`YYYYMMDD_描述.sql` = 某天的变更；`NN_描述.sql` = 编号步骤（步骤之间有先后依赖）。带 `_` 前缀的是**临时检查脚本**，用完即删。

## 脚本分类

判断一个脚本能不能删，看它属于哪一类：

| 类别 | 特征 | 能不能删 |
|---|---|---|
| **结构变更** | `CREATE TABLE` / `ALTER TABLE` / `RENAME` | ❌ 不能删 —— 这是库结构的唯一来源，新环境建库靠它 |
| **种子数据** | `INSERT` 菜单、账号、评价、知识库等展示/配置数据 | ❌ 不能删 —— 删了重建库就没这些数据 |
| **数据修正** | 对**将来还会发生**的数据做修正（如按商品关键词修正 SKU 规格、给存量 body 补文字、清理已关闭预警的库存记录） | ❌ 不能删 —— 下一批数据还会用到 |
| **一次性回填** | 补**已经过去**的历史数据（如给历史订单补收款单、给历史库存变动补流水） | ✅ 可删 —— 新库没有这些历史数据，补了也没意义 |
| **临时工具** | 纯查询检查（`_inspect.sql`、`_verify_ai.sql`） | ✅ 可删 |
| **重复脚本** | 内容已被别的脚本完全覆盖，或含无法直接执行的占位符 | ✅ 可删 |

## 已归档不入库的一次性脚本

下面这些曾在本地+生产执行过，**脚本本身不保留在仓库**（文档里引用时标注「已执行，未入库」）：

| 脚本 | 作用 |
|---|---|
| `add-admin-soft-delete.sql` | `admin` 表加 `is_deleted` 软删列（结构已并入 `20261105_admin_login_log.sql` 等现行脚本） |
| `user-pc-auth.sql` | `user` 表加 `username` / `password` / `uk_user_username` 索引 |
| `check-local-prod-parity.sql` | 本地↔生产库指纹比对（**已丢失**，需要时自己写或用 `mysqldump --no-data` 比结构） |
| `sync-local-to-prod.sql` / `sync-prod-to-local.sql` | 两库数据同步 |
| `normalize-sandbox-ids.sql` | 沙箱期 ID 归一化（沙箱已下线） |
| `fix-product-pricing.sql` / `fix-product-pricing-preview.sql` | 商品价格修正 |
| `drop-confirm-at.sql` | 删除 `confirm_at` 列 |
| `order-status-merge-pending-confirm.sql` | 订单状态合并 |
| `gen-admin5-transfer.sql` | 管理员账号转移 |
| `seed-legal-articles.sql` | 用户协议/隐私政策种子（**由 `tools/gen_legal_seed.py` 生成**，不需入库） |
| `stock-alert-data.sql` | 库存预警种子数据（导出件） |
| `product-image-cache-version.sql` | 商品图换缓存版本号 |

## 写脚本的要求

1. **幂等**：加列/加索引前先查 `information_schema`，可重复执行（`SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE ...)` + `PREPARE`/`EXECUTE` 的写法，参考 `20261113_orders_unpaid_notify.sql`）。
2. **MySQL 5.7 兼容**：生产是 5.7，**不能用 `utf8mb4_0900_ai_ci`**（8.0 专有），新表显式写 `COLLATE utf8mb4_general_ci`。
3. **带上执行说明**：脚本头部写清楚「改什么 / 何时执行 / 前置条件 / 是否幂等 / 兼容版本」。
4. **执行完回查**：改完在生产跑完要回查结果（`information_schema` 或直接 SELECT）。
5. **时间戳统一用 Java 传参**：服务器时区是 UTC，`NOW()` 比 `LocalDateTime.now()` 早 8 小时。
