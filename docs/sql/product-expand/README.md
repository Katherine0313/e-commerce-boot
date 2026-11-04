# 商品数据套件（product-expand）

商品种子 + 标题/副标题/参数/图片的最终形态。**从零建库时按下面的顺序跑一遍**，跑完商品数据就是最终状态。

> 早期的结构变更脚本（`00-precheck` / `01-specs-column` / `02-category-refactor` / `03-backfill-specs` /
> `03a-remove-old-seed` / `06a-add-title` / `06b-drop-description` / `08-fix-seed-data` /
> `09-merge-watch-category`）已执行完毕并删除 —— 它们要干的活现在都在 `product.sql`（`specs`/`title` 列、
> 8 一级 + 21 二级分类）和下面的数据脚本里。需要追溯看 git 历史。

## 文件

| 文件 | 作用 |
|---|---|
| `04-products-seed.sql` | 种子商品（id 51~126，`INSERT IGNORE`，含 specs） |
| `05-fix-titles.sql` | 重写 126 件商品主标题（名称 + 卖点 + 参数 + 官方正品） |
| `05-indexes.sql` | 商品分页/筛选索引 |
| `07-fix-subtitles.sql` | 淘宝风格副标题（销量 + 可开发票 + 品类口碑） |
| `10-mechanical-watches.sql` | 机械普通手表分类 + 10 款机械/石英表（id 127~136） |
| `11-backfill-body-images.sql` | id 51~105 的图文详情 body（`/image/body/{id}-1.webp`、`{id}-2.webp`） |
| `12-fix-product-titles-specs.sql` | **人工核对后的 title/specs 修正（权威版本，id 1~126）** |
| `13-fix-mechanical-watches.sql` | 机械表参数修正（127~136） |
| `14-product-images-106-136.sql` | 106~136 的主图 / 缩略图（128 无图） |
| `99-verify.sql` | 只读校验（数量 / specs / 图片） |

## 执行顺序

```bash
cd /d/study/my-workspace/wechat-workspace/e-commerce-boot
mysql ... e_commerce_db < docs/sql/product-expand/04-products-seed.sql
mysql ... e_commerce_db < docs/sql/product-expand/05-indexes.sql
mysql ... e_commerce_db < docs/sql/product-expand/05-fix-titles.sql
mysql ... e_commerce_db < docs/sql/product-expand/07-fix-subtitles.sql
mysql ... e_commerce_db < docs/sql/product-expand/10-mechanical-watches.sql
mysql ... e_commerce_db < docs/sql/product-expand/11-backfill-body-images.sql
mysql ... e_commerce_db < docs/sql/product-expand/12-fix-product-titles-specs.sql
mysql ... e_commerce_db < docs/sql/product-expand/13-fix-mechanical-watches.sql
mysql ... e_commerce_db < docs/sql/product-expand/14-product-images-106-136.sql
mysql ... e_commerce_db < docs/sql/product-expand/99-verify.sql
```

跑完清一下分类缓存（分类结构变了）：

```bash
redis-cli --scan --pattern 'category:parent:*' | xargs -r redis-cli DEL
```

生产执行：备份 → 上传 `product-expand/` → 用 `deploy/run-sql-prod.sh` 依次跑 → 同步图片文件
（本机 `upload/image/upload/51~136.webp`、`upload/image/opt/upload/` → 服务器 `/www/java/upload/image/` 对应目录）
→ 清 Redis 分类缓存。

## 生成器（改商品先改源，别手改 SQL）

```bash
cd /d/study/my-workspace/wechat-workspace/e-commerce-boot
DB_USERNAME=e_commerce_db DB_PASSWORD=<本地密码> \
  ../recommender-python/.venv/Scripts/python.exe \
  docs/sql/product-expand/generator/generate_catalog.py
```

| 脚本 | 作用 |
|---|---|
| `generate_catalog.py` | 生成种子商品 SQL 与 51~126 占位图 |
| `fix_titles.py` | 重新生成 `05-fix-titles.sql` |
| `fix_subtitles.py` | 重新生成 `07-fix-subtitles.sql`（按 id 稳定生成营销数字） |
| `apply_curation.py` | 按 `product_curation.json` 生成 `12-fix-product-titles-specs.sql` |
| `add_mechanical_watches.py` | 生成机械表种子（127~136） |
| `convert_product_images.py` | 桌面原图 PNG → WebP（主图 800/400 opt、详情图 1200） |
| `rebuild_opt_images.py` | 重建缩略图 |

生成器只读数据库、输出 SQL 和图片，不直接写库；随机数以商品 id 为种子，重复运行结果一致。
