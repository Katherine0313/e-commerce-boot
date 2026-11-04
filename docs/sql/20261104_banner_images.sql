-- =============================================================================
-- Banner 支持最多 4 张图：新增 images 列（生产 MySQL 5.7 兼容）
-- 可重复执行：先检查列是否存在
-- =============================================================================

SET @has_images := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'promotion_banner'
    AND COLUMN_NAME = 'images'
);
SET @ddl := IF(@has_images = 0,
  'ALTER TABLE promotion_banner ADD COLUMN images TEXT NULL COMMENT ''图片地址 JSON 数组（最多 4 张，首张与 image 相同）'' AFTER image',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'promotion_banner' AND COLUMN_NAME = 'images';
