-- =====================================================================
-- 06b：删除 products.description（新代码已上线并只读写 title 后执行）
-- 可重复执行
-- =====================================================================
SET NAMES utf8mb4;

SET @ddl := (
  SELECT IF(COUNT(*) > 0,
    'ALTER TABLE products DROP COLUMN description',
    'DO 0')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND COLUMN_NAME = 'description'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SHOW COLUMNS FROM products LIKE 'description';
SHOW COLUMNS FROM products LIKE 'title';
