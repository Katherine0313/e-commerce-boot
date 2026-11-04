# SQL 脚本说明

本目录是**建库脚本 + 种子数据 + 运维工具**。一次性迁移/修复脚本（结构变更、数据订正）已执行完毕并从本目录删除，需要追溯时看 git 历史。

## 通用规则

- **导入必须加 `--default-character-set=utf8mb4`**，否则 Windows 的 mysql 客户端按 GBK 读，中文乱码。
- **MySQL 5.7 兼容**：排序规则写死 `utf8mb4_general_ci`（5.7 不支持 8.0 默认的 `utf8mb4_0900_ai_ci`）。
- **时间戳一律 Java 传参**，不要在 SQL 里用 `NOW()`：MySQL 服务器时区是 UTC，`NOW()` 比 JVM 写的 `LocalDateTime.now()` 早 8 小时，混用会让按时间筛选错乱。
- **改本地库必须同步生产**：同一份 SQL 两边都执行，执行前先备份（生产：`bash /www/java/backup-prod-db.sh`）。
- 大部分脚本幂等；带「一次性」字样的脚本重复执行会报错。

## 从零建一个库

### 1. 建表（按模块）

```bash
mysql ... e_commerce_db < docs/sql/admin.sql
mysql ... e_commerce_db < docs/sql/add-admin-soft-delete.sql   # admin 表加 is_deleted
mysql ... e_commerce_db < docs/sql/product.sql
mysql ... e_commerce_db < docs/sql/order.sql
mysql ... e_commerce_db < docs/sql/after-sale/01-after-sale-log-schema.sql
mysql ... e_commerce_db < docs/sql/coupon/01-coupon-schema.sql
mysql ... e_commerce_db < docs/sql/log/01-operation-log-schema.sql
mysql ... e_commerce_db < docs/sql/member/01-member-schema.sql
mysql ... e_commerce_db < docs/sql/promotion/01-promotion-schema.sql
mysql ... e_commerce_db < docs/sql/promotion/02-banner-article-schema.sql
mysql ... e_commerce_db < docs/sql/rbac/01-rbac-schema.sql
mysql ... e_commerce_db < docs/sql/review/01-admin-review-schema.sql
mysql ... e_commerce_db < docs/sql/shipment/01-shipment-schema.sql
```

### 2. 表结构补丁（这两个表不是从零建的）

```bash
mysql ... e_commerce_db < docs/sql/stock/01-rename-stock-alert.sql  # 库存预警表 stock → stock_alert
mysql ... e_commerce_db < docs/sql/user-pc-auth.sql                  # user 表加 PC 登录 username/password 与索引
```

> `user` 表不在本目录（由 user-service 维护），`stock_alert` 同理，所以这两个补丁必须单独跑。

### 3. 商品数据（`product-expand/`，必须按序）

```bash
04-products-seed.sql          # 种子商品（INSERT IGNORE）
05-fix-titles.sql             # 126 件 title 重写
05-indexes.sql                # 商品业务索引
07-fix-subtitles.sql          # 淘宝风格副标题
10-mechanical-watches.sql     # 机械表种子（127~136）
11-backfill-body-images.sql   # 详情正文图回填
12-fix-product-titles-specs.sql  # 人工核对后的 title/specs 修正（权威版本）
13-fix-mechanical-watches.sql # 机械表参数修正
14-product-images-106-136.sql # 106~136 的主图/缩略图
99-verify.sql                 # 校验（只读）
```

商品数据的源在 `product-expand/generator/`（Python 生成器 + `product_curation.json`），改动商品先改源再重跑生成器，别手改 SQL。

### 4. 其余种子数据

```bash
order-data.sql                # 订单种子
after-sale-data.sql           # 售后种子
stock-alert-data.sql          # 库存预警配置
shipment/02-delivery-express-seed.sql   # 快递公司字典（8 家）
rbac/02-drop-admin-role-column.sql      # 菜单/角色调整
rbac/03-fix-admin-name.sql
rbac/04-delivery-menu.sql               # 发货管理菜单
review/02-admin-review-menu.sql         # 商品评价菜单
```

### 5. 商品价格与图文

```bash
fix-product-pricing.sql           # 商品改价（真实售价）
fix-product-pricing-preview.sql   # 改价映射表（留档）
product-subtitle.sql              # 商品副标题
product-image-cache-version.sql   # 商品图 URL 追加 ?v= 版本号
```

> **商品图替换的规矩**：任何替换商品图文件的操作，**必须同时改 DB 里的 `image` 加版本号**。
> `/image/**` 响应头是 `Cache-Control: public, immutable, max-age=31536000`（`WebMvcConfig.java`），
> `immutable` 让客户端一年内不再回源 —— 只改文件不改 URL = 用户一年看不到新图（2026-10-31 踩过）。

### 6. 协议文章

```bash
seed-legal-articles.sql   # 小程序「用户协议」「隐私政策」
update-legal-articles.sql # 改版后按标题刷新正文
```

正文源文件在 `docs/legal/*.html`，改完跑 `python tools/gen_legal_seed.py` 重新生成上面两个脚本。

## 运维工具（长期可用）

| 脚本 | 用途 |
|---|---|
| `check-local-prod-parity.sql` | 比对本地库与生产库结构差异（只读） |
| `sync-local-to-prod.sql` | 本地 → 生产同步 |
| `sync-prod-to-local.sql` | 生产 → 本地同步 |
| `shipment/03-delivery-inspect.sql` | 发货数据巡检（只读） |

## 已删除的一次性脚本

结构迁移（沙箱下线、单店化）、数据订正（商品标题/参数修正）、已完成的列增删（`confirm_at`、`description`、`shop_id`）等一次性脚本已从本目录删除 —— 它们要干的活建表脚本里已经吸收了。需要追溯时看 git 历史（`git log --diff-filter=D -- docs/sql/`）。
