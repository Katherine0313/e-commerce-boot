-- =====================================================================
-- 商品扩容 05：分页/筛选索引（MySQL 5.7 兼容、可重复执行）
-- =====================================================================
SET NAMES utf8mb4;

SET @ddl := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE products ADD INDEX idx_products_list_status_cat_id (listing_status, category_id, id)',
    'DO 0')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products'
    AND INDEX_NAME = 'idx_products_list_status_cat_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE products ADD INDEX idx_products_list_status_price_id (listing_status, sales_price, id)',
    'DO 0')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products'
    AND INDEX_NAME = 'idx_products_list_status_price_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SHOW INDEX FROM products;
