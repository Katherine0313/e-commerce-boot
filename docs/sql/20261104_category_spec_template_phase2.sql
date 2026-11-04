-- =============================================================================
-- 阶段二：删除 products.weight / products.volume
-- 前置条件：新版 product-service 已上线且商品增删改查验证通过
-- 不可回滚，执行前请确认已有数据库备份
-- 可重复执行：删列前检查列是否存在
-- =============================================================================

-- 2.1 删除 products.weight
SET @has_weight := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'products'
    AND COLUMN_NAME = 'weight'
);
SET @ddl := IF(@has_weight = 1, 'ALTER TABLE products DROP COLUMN weight', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2.2 删除 products.volume
SET @has_volume := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'products'
    AND COLUMN_NAME = 'volume'
);
SET @ddl := IF(@has_volume = 1, 'ALTER TABLE products DROP COLUMN volume', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2.3 自检：应返回 0 行
SELECT COLUMN_NAME FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products'
  AND COLUMN_NAME IN ('weight', 'volume');
