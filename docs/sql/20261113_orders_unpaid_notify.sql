-- =============================================================================
-- 未支付订单订阅提醒：orders 表加 2 列 + 1 个复合索引
-- 2026-11-13
--
-- 用途：小程序下单时用户授权「未支付提醒」，order-service 按
--       「待支付 + 已授权 + 未推送」扫描订单并推送订阅消息，推送成功后标记已推送。
--       查询与标记见 order-service 的 OrderMapper.xml。
--
-- 执行时机：order-service 新版本上线【前】——新代码会读写这两列，缺列会直接报错。
-- 当前状态：**本地库与生产库均已存在这两列和索引**（2026-10-25 随未支付订阅消息上线执行）；
--            本脚本为补归档，用于新环境 / 重建库时初始化，复用即幂等。
-- 幂等：加列 / 加索引前先查 information_schema，可重复执行。
-- 兼容：生产 MySQL 5.7 / 8.0 均可。
--
-- ⚠ 必须带库名执行（下面用 DATABASE() 判断当前库，没选库会判断失效）：
--   生产：bash deploy/run-sql-prod.sh /www/java/sql/20261113_orders_unpaid_notify.sql
--   本地：mysql -D e_commerce_db < docs/sql/20261113_orders_unpaid_notify.sql
-- =============================================================================

-- 守卫：没选库时打印提示（此时 ALTER 也会因 "No database selected" 直接失败，不会误改数据）
SELECT IF(DATABASE() IS NULL,
       '错误：必须带库名执行，例如 mysql -D e_commerce_db < 本脚本.sql',
       CONCAT('当前库：', DATABASE(), '，开始执行')) AS _guard;

-- 1) is_subscribe_unpaid：是否授权未支付提醒
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'is_subscribe_unpaid'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE `orders` ADD COLUMN `is_subscribe_unpaid` TINYINT NOT NULL DEFAULT 0 COMMENT ''是否授权未支付提醒：1是 0否'' AFTER `sign_status`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) unpaid_notify_sent：未支付提醒是否已推送（推送成功后置 1，避免重复推送）
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'unpaid_notify_sent'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE `orders` ADD COLUMN `unpaid_notify_sent` TINYINT NOT NULL DEFAULT 0 COMMENT ''未支付提醒是否已推送：1是 0否'' AFTER `is_subscribe_unpaid`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 复合索引：待推送订单扫描走这条索引
--    (order_status, is_subscribe_unpaid, unpaid_notify_sent, created_at)
SET @exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'idx_orders_unpaid_notify'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE `orders` ADD INDEX `idx_orders_unpaid_notify` (`order_status`, `is_subscribe_unpaid`, `unpaid_notify_sent`, `created_at`)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
