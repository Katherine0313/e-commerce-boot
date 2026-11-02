-- ============================================================================
-- 后台商品评价管理：product_reviews 扩列 + 索引（2026-11-02）
--
-- 背景：原始需求写「已经 ALTER 过 reply/reply_time/visible/is_virtual/update_time/del_flag」，
--        但 2026-11-02 实测【本地与生产库结构完全一致，且从未 ALTER 过】——
--        这 6 个字段一个都不存在。核对结果（SHOW COLUMNS FROM product_reviews）：
--          id, order_id, user_id, product_id, product_name, product_image,
--          rating, tag, content, images, status, is_deleted, created_at, updated_at
--
-- 本脚本只【新增】4 列，不改已有列：
--   * visible   → 复用已有的 status（注释就是「1 显示 0 隐藏」），不新增
--   * del_flag  → 复用已有的 is_deleted，不新增
--   * update_time → 复用已有的 updated_at，不新增
--   * 新增 reply / reply_time / is_virtual / created_by
--
-- 加列是纯新增，历史数据不受影响：is_virtual 自动为 0（真实评价）、
-- created_by 自动为 NULL，【无需回填】。
--
-- 幂等：可重复执行。加列用 information_schema 判断是否已存在（生产是 MySQL 5.7，
--       不支持 ADD COLUMN IF NOT EXISTS）；索引同理。
--
-- 执行前请先备份数据库。
--   本地：mysql -u<user> -p<pass> --default-character-set=utf8mb4 e_commerce_db < 01-admin-review-schema.sql
--   生产：先 mysqldump 备份，再执行同一份（经 stdin 管道导入，服务器不落文件）
-- ============================================================================

-- ------------------------------------------------------------
-- 1. reply：商家回复正文（一条评价只保留一条回复，再次回复覆盖）
-- ------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'product_reviews'
    AND COLUMN_NAME  = 'reply'
);

SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `product_reviews` ADD COLUMN `reply` VARCHAR(1000) DEFAULT NULL COMMENT ''商家回复''',
  'SELECT ''reply 已存在，跳过'' AS message'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. reply_time：商家回复时间（Java 传参，不用 MySQL NOW()，避免 8 小时时区差）
-- ------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'product_reviews'
    AND COLUMN_NAME  = 'reply_time'
);

SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `product_reviews` ADD COLUMN `reply_time` DATETIME DEFAULT NULL COMMENT ''商家回复时间''',
  'SELECT ''reply_time 已存在，跳过'' AS message'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 3. is_virtual：1 虚拟评价（后台造的）/ 0 买家真实评价
-- ------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'product_reviews'
    AND COLUMN_NAME  = 'is_virtual'
);

SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `product_reviews` ADD COLUMN `is_virtual` TINYINT NOT NULL DEFAULT 0 COMMENT ''是否虚拟评价：1 虚拟 0 买家真实''',
  'SELECT ''is_virtual 已存在，跳过'' AS message'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 4. created_by：虚拟评价创建人（后台管理员 admin.id）
--    ⚠️ 存的是 admin.id，跟 C 端的 user.id 是两套 ID 空间；
--       本列只有后台写入（建虚拟评价时），买家端永远不写，所以不冲突。
-- ------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'product_reviews'
    AND COLUMN_NAME  = 'created_by'
);

SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `product_reviews` ADD COLUMN `created_by` BIGINT DEFAULT NULL COMMENT ''虚拟评价创建人（后台管理员 admin.id）''',
  'SELECT ''created_by 已存在，跳过'' AS message'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 5. 索引
--    idx_review_admin：后台列表固定 WHERE is_deleted=0 + ORDER BY created_at DESC
--    idx_review_product_show：C 端按商品查可见评价（product_id + status + is_deleted + created_at）
-- ------------------------------------------------------------
SET @idx_exists := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'product_reviews'
    AND INDEX_NAME   = 'idx_review_admin'
);

SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE `product_reviews` ADD INDEX `idx_review_admin` (`is_deleted`, `status`, `created_at`)',
  'SELECT ''idx_review_admin 已存在，跳过'' AS message'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'product_reviews'
    AND INDEX_NAME   = 'idx_review_product_show'
);

SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE `product_reviews` ADD INDEX `idx_review_product_show` (`product_id`, `status`, `is_deleted`, `created_at`)',
  'SELECT ''idx_review_product_show 已存在，跳过'' AS message'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 校验 1：应有 4 个新列，且 is_virtual 默认 0
-- ============================================================
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME   = 'product_reviews'
  AND COLUMN_NAME IN ('reply', 'reply_time', 'is_virtual', 'created_by')
ORDER BY ORDINAL_POSITION;

-- ============================================================
-- 校验 2：索引应存在
-- ============================================================
SELECT INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME   = 'product_reviews'
  AND INDEX_NAME IN ('idx_review_admin', 'idx_review_product_show')
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

-- ============================================================
-- 校验 3：历史数据的 is_virtual 应全为 0（都是买家真实评价），created_by 全为 NULL
-- ============================================================
SELECT is_virtual, COUNT(*) AS cnt, SUM(created_by IS NULL) AS null_creator
FROM product_reviews
GROUP BY is_virtual;
