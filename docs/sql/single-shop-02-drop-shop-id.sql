-- =====================================================================
-- 单店化 02：删除 shop_id 列、含 shop_id 的索引、shops 表
-- ---------------------------------------------------------------------
-- 执行时机：所有后端实例都已升级到单店版代码、shop-service 容器已停止之后。
-- 执行前务必全量备份；本脚本会重建表结构，不可仅靠回滚镜像恢复。
-- MySQL 5.7 兼容：不使用 DROP ... IF EXISTS / CTE / 窗口函数。
-- 脚本通过 information_schema 判断，可重复执行。
-- =====================================================================
SET NAMES utf8mb4;

DELIMITER $$

-- 断言：18 张表已无 shop_id <> 1 的数据，否则中止
DROP PROCEDURE IF EXISTS _single_shop_assert_clean$$
CREATE PROCEDURE _single_shop_assert_clean()
BEGIN
  DECLARE c BIGINT DEFAULT 0;
  SELECT
      (SELECT COUNT(*) FROM `address` WHERE shop_id <> 1)
    + (SELECT COUNT(*) FROM `admin` WHERE shop_id <> 1)
    + (SELECT COUNT(*) FROM `after_sale_exchange_returns` WHERE shop_id <> 1)
    + (SELECT COUNT(*) FROM `after_sale_items` WHERE shop_id <> 1)
    + (SELECT COUNT(*) FROM `after_sale_only_refunds` WHERE shop_id <> 1)
    + (SELECT COUNT(*) FROM `after_sale_returns` WHERE shop_id <> 1)
    + (SELECT COUNT(*) FROM `after_sales` WHERE shop_id <> 1)
    + (SELECT COUNT(*) FROM `cart` WHERE shop_id <> 1)
    + (SELECT COUNT(*) FROM `categories` WHERE shop_id <> 1)
    + (SELECT COUNT(*) FROM `feedback` WHERE shop_id <> 1)
    + (SELECT COUNT(*) FROM `order_items` WHERE shop_id <> 1)
    + (SELECT COUNT(*) FROM `orders` WHERE shop_id <> 1)
    + (SELECT COUNT(*) FROM `product_favorites` WHERE shop_id <> 1)
    + (SELECT COUNT(*) FROM `product_reviews` WHERE shop_id <> 1)
    + (SELECT COUNT(*) FROM `products` WHERE shop_id <> 1)
    + (SELECT COUNT(*) FROM `stock` WHERE shop_id <> 1)
    + (SELECT COUNT(*) FROM `tags` WHERE shop_id <> 1)
    + (SELECT COUNT(*) FROM `user` WHERE shop_id <> 1)
  INTO c;
  IF c > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'single-shop-02: 仍存在非主店数据，请先执行 single-shop-01-cleanup.sql';
  END IF;
END$$

-- 删除索引（存在才删）
DROP PROCEDURE IF EXISTS _single_shop_drop_index$$
CREATE PROCEDURE _single_shop_drop_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64))
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table, '` DROP INDEX `', p_index, '`');
    PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
  END IF;
END$$

-- 删除列（存在才删）
DROP PROCEDURE IF EXISTS _single_shop_drop_column$$
CREATE PROCEDURE _single_shop_drop_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64))
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table, '` DROP COLUMN `', p_column, '`');
    PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
  END IF;
END$$

-- 确保唯一键存在（列集合相同则跳过）
DROP PROCEDURE IF EXISTS _single_shop_ensure_unique$$
CREATE PROCEDURE _single_shop_ensure_unique(
  IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM (
      SELECT INDEX_NAME,
             GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
      FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND NON_UNIQUE = 0
      GROUP BY INDEX_NAME
    ) x WHERE REPLACE(x.cols, '`', '') = REPLACE(p_cols, '`', '')
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD UNIQUE KEY `', p_index, '` (', p_cols, ')');
    PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
  END IF;
END$$

-- 确保普通索引存在（同名索引存在则跳过）
DROP PROCEDURE IF EXISTS _single_shop_ensure_index$$
CREATE PROCEDURE _single_shop_ensure_index(
  IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD INDEX `', p_index, '` (', p_cols, ')');
    PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
  END IF;
END$$

DELIMITER ;

-- 0. 前置断言
CALL _single_shop_assert_clean();

-- 1. 删除含 shop_id 的索引（唯一键先删，稍后按单店口径重建）
CALL _single_shop_drop_index('user', 'uk_shop_openid');
CALL _single_shop_drop_index('product_favorites', 'uk_shop_user_product');
CALL _single_shop_drop_index('product_favorites', 'idx_shop_user_time');
CALL _single_shop_drop_index('after_sale_items', 'idx_after_sale_items_shop');
CALL _single_shop_drop_index('after_sales', 'idx_after_sales_shop');
CALL _single_shop_drop_index('feedback', 'idx_feedback_shop_created');
-- 下面两个只在部分环境存在（order-index-migrate.sql 声明过，本地实库可能没有）
CALL _single_shop_drop_index('orders', 'idx_orders_shop_user');
CALL _single_shop_drop_index('order_items', 'idx_order_shop');

-- 2. 删除 18 张表的 shop_id 列
CALL _single_shop_drop_column('address', 'shop_id');
CALL _single_shop_drop_column('admin', 'shop_id');
CALL _single_shop_drop_column('after_sale_exchange_returns', 'shop_id');
CALL _single_shop_drop_column('after_sale_items', 'shop_id');
CALL _single_shop_drop_column('after_sale_only_refunds', 'shop_id');
CALL _single_shop_drop_column('after_sale_returns', 'shop_id');
CALL _single_shop_drop_column('after_sales', 'shop_id');
CALL _single_shop_drop_column('cart', 'shop_id');
CALL _single_shop_drop_column('categories', 'shop_id');
CALL _single_shop_drop_column('feedback', 'shop_id');
CALL _single_shop_drop_column('order_items', 'shop_id');
CALL _single_shop_drop_column('orders', 'shop_id');
CALL _single_shop_drop_column('product_favorites', 'shop_id');
CALL _single_shop_drop_column('product_reviews', 'shop_id');
CALL _single_shop_drop_column('products', 'shop_id');
CALL _single_shop_drop_column('stock', 'shop_id');
CALL _single_shop_drop_column('tags', 'shop_id');
CALL _single_shop_drop_column('user', 'shop_id');

-- 3. 按单店口径重建唯一键 / 索引
CALL _single_shop_ensure_unique('user', 'uk_openid', '`openid`');
CALL _single_shop_ensure_unique('product_favorites', 'uk_user_product', '`user_id`,`product_id`');
CALL _single_shop_ensure_index('product_favorites', 'idx_favorites_user_time', '`user_id`,`created_at`');

-- 4. 删除店铺主表
DROP TABLE IF EXISTS `shops`;

-- 5. 清理临时存储过程
DROP PROCEDURE IF EXISTS _single_shop_assert_clean;
DROP PROCEDURE IF EXISTS _single_shop_drop_index;
DROP PROCEDURE IF EXISTS _single_shop_drop_column;
DROP PROCEDURE IF EXISTS _single_shop_ensure_unique;
DROP PROCEDURE IF EXISTS _single_shop_ensure_index;

-- =====================================================================
-- 6. 自检（期望：第一条查询 0 行；第二条 0 行；第三条 0 行）
-- =====================================================================
SELECT TABLE_NAME, COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'shop_id';

SELECT TABLE_NAME, INDEX_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'shop_id'
GROUP BY TABLE_NAME, INDEX_NAME;

SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shops';

SHOW INDEX FROM `user`;
SHOW INDEX FROM `product_favorites`;
