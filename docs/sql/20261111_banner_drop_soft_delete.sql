-- =============================================================================
-- promotion_banner 去掉软删列 is_deleted：删除一律物理删除
-- 背景：BannerMapper.deleteById 早已是 DELETE，列 is_deleted 只剩「恒为 0」的冗余
-- 可重复执行；生产 MySQL 5.7 兼容
-- =============================================================================

-- 1) 先把历史上软删的行真正删掉，否则 DROP 列后它们会重新出现在列表里
SET @has_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'promotion_banner'
    AND COLUMN_NAME = 'is_deleted'
);
SET @sql_delete := IF(@has_col > 0,
  'DELETE FROM promotion_banner WHERE is_deleted = 1',
  'SELECT 1');
PREPARE stmt FROM @sql_delete;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 删除冗余列
SET @sql_drop := IF(@has_col > 0,
  'ALTER TABLE promotion_banner DROP COLUMN is_deleted',
  'SELECT 1');
PREPARE stmt FROM @sql_drop;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) 执行结果：正常应只有 11 列，且不含 is_deleted
SELECT COLUMN_NAME, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'promotion_banner'
ORDER BY ORDINAL_POSITION;
