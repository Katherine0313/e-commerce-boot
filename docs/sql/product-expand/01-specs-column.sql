-- =====================================================================
-- 商品扩容 01：products 增加 specs JSON 列（MySQL 5.7 兼容、可重复执行）
-- specs 存有序数组：[{"name":"屏幕","value":"6.1英寸"}]
-- =====================================================================
SET NAMES utf8mb4;

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE products ADD COLUMN specs JSON NULL COMMENT ''规格参数(JSON数组，非SKU)'' AFTER volume',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'products'
    AND COLUMN_NAME = 'specs'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 验证
SHOW COLUMNS FROM products LIKE 'specs';
