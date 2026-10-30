# SQL 脚本说明

## 单店化迁移（2026-10-29）

项目已彻底下线多租户沙箱，`shop_id` 全部删除，只保留一家店。迁移分两步，**顺序不能反**：

### 1. 备份（必须）

```bash
mysqldump -h127.0.0.1 -P3306 -u<user> -p \
  --single-transaction --routines --triggers \
  --default-character-set=utf8mb4 e_commerce_db \
  > e_commerce_db_before_single_shop_$(date +%Y%m%d%H%M).sql
```

同时备份当前镜像 tag、`deploy/docker-compose.override.yml`、后台前端 `dist`。

### 1.5 线上先跑只读预检查

```bash
mysql ... e_commerce_db < docs/sql/single-shop-00-precheck.sql
```

- 第 1 节全部为 0：线上没有沙箱数据，01 不会删业务数据
- 第 1 节有非 0：确认这些是非主店（沙箱）数据后再执行 01
- 第 2 节必须全部为 0，第 3 节必须为空

### 2. 执行 `single-shop-01-cleanup.sql`

- 时机：**新代码发布前**
- 作用：清理 `shop_id <> 1` 的沙箱数据；给 `product_favorites.shop_id` 补 `DEFAULT 1`
- 不修改表结构，可重复执行
- 执行后复核各表非主店计数为 0

```bash
mysql -h127.0.0.1 -u<user> -p --default-character-set=utf8mb4 e_commerce_db \
  < docs/sql/single-shop-01-cleanup.sql
```

### 3. 部署单店版后端

按顺序发布：`product-service` → `order-service` → `aftersale-service` → `user-service` → `ai-service` → 后台前端 → `gateway` → **最后 `auth-service`**。

> `auth-service` 必须最后发：它停止签发 `shopId` 后，旧版本服务会拒绝新 token。
> 迁移当天不要直接 `./deploy/release.sh all`（虽然脚本已把 auth 排到最后，仍建议逐服务确认）。

### 4. 执行 `single-shop-02-drop-shop-id.sql`

- 时机：**所有后端实例都已是单店版、shop-service 容器已停止之后**
- 作用：删除 18 张表的 `shop_id`、含 `shop_id` 的索引、`shops` 表；重建 `user.uk_openid(openid)`、`product_favorites.uk_user_product(user_id, product_id)`
- 带前置断言：仍有非主店数据会直接报错中止
- MySQL 5.7 兼容，可重复执行（内部按 `information_schema` 判断）

```bash
mysql -h127.0.0.1 -u<user> -p --default-character-set=utf8mb4 e_commerce_db \
  < docs/sql/single-shop-02-drop-shop-id.sql
```

### 5. 回滚

- 02 执行前：回滚镜像即可（沙箱数据已删，主店无损）
- 02 执行后：必须「恢复数据库备份 + 回滚旧镜像」，旧代码无法在无 `shop_id` 的表上运行

## 已归档脚本

`sandbox-migrate.sql`、`sandbox-visitor-key.sql` 属于已下线的多租户机制，移到 `archive/`，**不要在新建库上执行**。

## 其他历史脚本

`admin.sql`、`product.sql`、`order.sql`、`order-data.sql`、`after-sale-data.sql`、`stock-alert-data.sql` 已按单店口径更新（无 `shop_id`、无 `shops` 表）。库存预警表已从 `stock` 改名为 `stock_alert`（当前库存仍在 `products.stock`）。
`order-index-migrate.sql`、`order-status-migrate.sql`、`product-subtitle.sql` 为历史增量脚本，保留备查。
`user-pc-auth.sql`：PC 商城登录/注册，为 `user` 表新增 `password` 列和 `idx_user_phone` 索引（需在重启 user-service 前执行）。
`stock/01-rename-stock-alert.sql`：库存预警表 `stock` → `stock_alert`（幂等，只改表名/索引名/外键名，不动数据）。
