# 商品扩容（specs + 分类重构 + 300 商品）

> 2026-10-29。本轮只改后端和数据，前端（小程序/后台/PC）后续再接。

## 文件

| 文件 | 作用 |
|---|---|
| `00-precheck.sql` | 只读预检：版本、id 冲突、分类/商品数量 |
| `01-specs-column.sql` | `products.specs JSON NULL`（幂等） |
| `02-category-refactor.sql` | 8 个一级 + 21 个二级（幂等） |
| `03-backfill-specs.sql` | 现有 50 个商品补 specs |
| `04-products-51-300.sql` | 250 个种子商品（id 51~300，`SEED-xxx`） |
| `05-indexes.sql` | 分页/筛选索引（幂等） |
| `99-verify.sql` | 数量/specs/图片校验 |
| `generator/generate_catalog.py` | 重新生成 03/04 和 51~300 图片 |

## 执行顺序

> **必须先执行 01，再部署带 specs 的 product-service**；否则新 Mapper 会报未知列。
> 02/03/04 可以在代码前后执行；旧代码不查 specs。

### 本地

```bash
cd /d/study/my-workspace/wechat-workspace/e-commerce-boot
mysql ... e_commerce_db < docs/sql/product-expand/00-precheck.sql
mysql ... e_commerce_db < docs/sql/product-expand/01-specs-column.sql
mysql ... e_commerce_db < docs/sql/product-expand/02-category-refactor.sql
mysql ... e_commerce_db < docs/sql/product-expand/03-backfill-specs.sql
mysql ... e_commerce_db < docs/sql/product-expand/04-products-51-300.sql
mysql ... e_commerce_db < docs/sql/product-expand/05-indexes.sql
mysql ... e_commerce_db < docs/sql/product-expand/99-verify.sql
```

清分类缓存：

```bash
redis-cli --scan --pattern 'category:parent:*' | xargs -r redis-cli DEL
```

### 生产

1. 备份数据库
2. 上传 `product-expand/` 到服务器
3. 依次执行 00/01/02/03/04/05/99（用 `deploy/run-sql-prod.sh`）
4. 同步图片：本机 `upload/image/upload/51~300.webp` 和 `upload/image/opt/upload/51~300.webp` → 服务器 `/www/java/upload/image/` 对应目录
5. 清 Redis `category:parent:*`
6. 部署 product-service

## 生成器

```bash
cd /d/study/my-workspace/wechat-workspace/e-commerce-boot
DB_USERNAME=e_commerce_db DB_PASSWORD=<本地密码> \
  ../recommender-python/.venv/Scripts/python.exe \
  docs/sql/product-expand/generator/generate_catalog.py
```

- 只读数据库读取现有 50 个商品，输出 SQL 和图片，不直接写库
- 随机数以商品 id 为种子，重复运行结果一致
- 图片复用同品类现有图并叠加 `SEED-id`，文件在 `upload/image/upload/` 与 `opt/upload/`

## 回滚

1. 删种子商品：`DELETE FROM products WHERE id BETWEEN 51 AND 300 AND product_code LIKE 'SEED-%';`（注意 cart/stock 外键，必要时先清引用或改为下架）
2. 删新分类：先删 701~704、801~804，再删 7、8（`categories` 自引用 CASCADE；商品 `category_id` 会 SET NULL）
3. 恢复 id=4 名称（如需要）
4. 现有 50 个 specs 置 NULL（如需要）
5. 删除 51~300 图片
