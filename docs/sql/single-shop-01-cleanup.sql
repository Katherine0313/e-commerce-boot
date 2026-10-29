-- =====================================================================
-- 单店化 01：清理非主店（沙箱店）数据
-- ---------------------------------------------------------------------
-- 执行时机：新代码发布【前】。旧代码仍按 shop_id 过滤，清理期间主店不受影响。
-- 执行前务必全量备份（见 docs/sql/README.md）。
-- 本脚本只删 shop_id <> 1 的数据，不修改表结构，可重复执行。
-- =====================================================================
SET NAMES utf8mb4;
SET @keep_shop_id := 1;

-- ---------------------------------------------------------------------
-- 1. 预检查：各表非主店数据量（先看一眼，确认要删的范围）
-- ---------------------------------------------------------------------
SELECT 'address' AS table_name, COUNT(*) AS non_keep FROM `address` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'admin', COUNT(*) FROM `admin` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'after_sale_exchange_returns', COUNT(*) FROM `after_sale_exchange_returns` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'after_sale_items', COUNT(*) FROM `after_sale_items` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'after_sale_only_refunds', COUNT(*) FROM `after_sale_only_refunds` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'after_sale_returns', COUNT(*) FROM `after_sale_returns` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'after_sales', COUNT(*) FROM `after_sales` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'cart', COUNT(*) FROM `cart` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'categories', COUNT(*) FROM `categories` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'feedback', COUNT(*) FROM `feedback` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'order_items', COUNT(*) FROM `order_items` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'orders', COUNT(*) FROM `orders` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'product_favorites', COUNT(*) FROM `product_favorites` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'product_reviews', COUNT(*) FROM `product_reviews` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'products', COUNT(*) FROM `products` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'stock', COUNT(*) FROM `stock` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'tags', COUNT(*) FROM `tags` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'user', COUNT(*) FROM `user` WHERE shop_id <> @keep_shop_id;

-- ---------------------------------------------------------------------
-- 2. 跨店外键异常检查：以下每项都应为 0，不为 0 先停手人工处理
-- ---------------------------------------------------------------------
SELECT 'categories.parent' AS k, COUNT(*) AS c
FROM `categories` c JOIN `categories` p ON c.parent_id = p.id WHERE c.shop_id <> p.shop_id
UNION ALL SELECT 'products.category', COUNT(*) FROM `products` p JOIN `categories` c ON p.category_id = c.id WHERE p.shop_id <> c.shop_id
UNION ALL SELECT 'orders.user', COUNT(*) FROM `orders` o JOIN `user` u ON o.user_id = u.id WHERE o.shop_id <> u.shop_id
UNION ALL SELECT 'order_items.order', COUNT(*) FROM `order_items` oi JOIN `orders` o ON oi.order_id = o.id WHERE oi.shop_id <> o.shop_id
UNION ALL SELECT 'after_sales.order', COUNT(*) FROM `after_sales` a JOIN `orders` o ON a.order_id = o.id WHERE a.shop_id <> o.shop_id
UNION ALL SELECT 'after_sale_items.after_sale', COUNT(*) FROM `after_sale_items` i JOIN `after_sales` a ON i.after_sale_id = a.id WHERE i.shop_id <> a.shop_id
UNION ALL SELECT 'after_sale_returns.after_sale', COUNT(*) FROM `after_sale_returns` r JOIN `after_sales` a ON r.after_sale_id = a.id WHERE r.shop_id <> a.shop_id
UNION ALL SELECT 'after_sale_exchange_returns.after_sale', COUNT(*) FROM `after_sale_exchange_returns` r JOIN `after_sales` a ON r.after_sale_id = a.id WHERE r.shop_id <> a.shop_id
UNION ALL SELECT 'after_sale_only_refunds.after_sale', COUNT(*) FROM `after_sale_only_refunds` r JOIN `after_sales` a ON r.after_sale_id = a.id WHERE r.shop_id <> a.shop_id
UNION ALL SELECT 'cart.user', COUNT(*) FROM `cart` c JOIN `user` u ON c.user_id = u.id WHERE c.shop_id <> u.shop_id
UNION ALL SELECT 'cart.product', COUNT(*) FROM `cart` c JOIN `products` p ON c.product_id = p.id WHERE c.shop_id <> p.shop_id
UNION ALL SELECT 'address.user', COUNT(*) FROM `address` ad JOIN `user` u ON ad.user_id = u.id WHERE ad.shop_id <> u.shop_id
UNION ALL SELECT 'stock.product', COUNT(*) FROM `stock` s JOIN `products` p ON s.product_id = p.id WHERE s.shop_id <> p.shop_id;

-- ---------------------------------------------------------------------
-- 3. 清理后唯一键冲突预检查（清理后应为空）
-- ---------------------------------------------------------------------
SELECT openid, COUNT(*) AS cnt FROM `user` GROUP BY openid HAVING COUNT(*) > 1;
SELECT user_id, product_id, COUNT(*) AS cnt FROM `product_favorites` GROUP BY user_id, product_id HAVING COUNT(*) > 1;

-- ---------------------------------------------------------------------
-- 4. product_favorites.shop_id 没有默认值：新代码 INSERT 不带该列会报错。
--    先补 DEFAULT 1，保证「新代码 + 旧表结构」的窗口期可正常收藏。
-- ---------------------------------------------------------------------
ALTER TABLE `product_favorites` MODIFY COLUMN `shop_id` BIGINT NOT NULL DEFAULT 1;

-- ---------------------------------------------------------------------
-- 5. 按外键依赖顺序清理非主店数据（顺序不可调整）
-- ---------------------------------------------------------------------
START TRANSACTION;

DELETE FROM `after_sale_exchange_returns` WHERE shop_id <> @keep_shop_id;
DELETE FROM `after_sale_only_refunds`     WHERE shop_id <> @keep_shop_id;
DELETE FROM `after_sale_returns`          WHERE shop_id <> @keep_shop_id;
DELETE FROM `after_sale_items`            WHERE shop_id <> @keep_shop_id;
DELETE FROM `after_sales`                 WHERE shop_id <> @keep_shop_id;

DELETE FROM `order_items`                 WHERE shop_id <> @keep_shop_id;
DELETE FROM `orders`                      WHERE shop_id <> @keep_shop_id;

DELETE FROM `cart`                        WHERE shop_id <> @keep_shop_id;
DELETE FROM `address`                     WHERE shop_id <> @keep_shop_id;
DELETE FROM `product_favorites`           WHERE shop_id <> @keep_shop_id;
DELETE FROM `product_reviews`             WHERE shop_id <> @keep_shop_id;
DELETE FROM `feedback`                    WHERE shop_id <> @keep_shop_id;

DELETE FROM `stock`                       WHERE shop_id <> @keep_shop_id;
DELETE FROM `products`                    WHERE shop_id <> @keep_shop_id;
DELETE FROM `user`                        WHERE shop_id <> @keep_shop_id;
DELETE FROM `admin`                       WHERE shop_id <> @keep_shop_id;
DELETE FROM `tags`                        WHERE shop_id <> @keep_shop_id;
DELETE FROM `categories`                  WHERE shop_id <> @keep_shop_id;

DELETE FROM `shops`                       WHERE id <> @keep_shop_id;

COMMIT;

-- ---------------------------------------------------------------------
-- 6. 复核：下面查询应全部为 0（重复执行第 1 节的计数 SQL 即可）
-- ---------------------------------------------------------------------
SELECT 'address' AS table_name, COUNT(*) AS non_keep FROM `address` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'admin', COUNT(*) FROM `admin` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'after_sale_exchange_returns', COUNT(*) FROM `after_sale_exchange_returns` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'after_sale_items', COUNT(*) FROM `after_sale_items` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'after_sale_only_refunds', COUNT(*) FROM `after_sale_only_refunds` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'after_sale_returns', COUNT(*) FROM `after_sale_returns` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'after_sales', COUNT(*) FROM `after_sales` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'cart', COUNT(*) FROM `cart` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'categories', COUNT(*) FROM `categories` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'feedback', COUNT(*) FROM `feedback` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'order_items', COUNT(*) FROM `order_items` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'orders', COUNT(*) FROM `orders` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'product_favorites', COUNT(*) FROM `product_favorites` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'product_reviews', COUNT(*) FROM `product_reviews` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'products', COUNT(*) FROM `products` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'stock', COUNT(*) FROM `stock` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'tags', COUNT(*) FROM `tags` WHERE shop_id <> @keep_shop_id
UNION ALL SELECT 'user', COUNT(*) FROM `user` WHERE shop_id <> @keep_shop_id;
