-- =====================================================================
-- 06a：products 增加 title 列并复制 description 数据（MySQL 5.7 兼容、可重复执行）
-- 先执行本脚本，再部署读取 title 的新代码；最后执行 06b 删除 description
-- =====================================================================
SET NAMES utf8mb4;

-- 1. 没有 title 才加
SET @ddl := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE products ADD COLUMN title TEXT NULL COMMENT ''主标题'' AFTER brand',
    'DO 0')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND COLUMN_NAME = 'title'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 有 description 就把值复制到 title（已存在 title 值的不覆盖）
SET @ddl := (
  SELECT IF(COUNT(*) > 0,
    'UPDATE products SET title = description WHERE title IS NULL',
    'DO 0')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND COLUMN_NAME = 'description'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SHOW COLUMNS FROM products LIKE 'title';
SELECT COUNT(*) AS title_null FROM products WHERE title IS NULL;
