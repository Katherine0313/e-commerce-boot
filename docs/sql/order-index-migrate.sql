-- orders / order_items 补业务索引（配合订单模块设计文档 5.3，幂等可重复执行）
--
-- 背景：优化超时订单扫描（findExpiredPendingPay）——
--   idx_orders_status_created(order_status, created_at)
--   docs/sql/order.sql 建表脚本已同步加该索引（新环境建表即有）；
--   本脚本用于给【已有库】补索引——order.sql 是 CREATE TABLE IF NOT EXISTS，改脚本不会改已有表。
--   多租户 shop_id 已下线（2026-10-29），原 idx_orders_shop_user / idx_order_shop 不再创建。
--
-- 执行方式（Windows 记得加 --default-character-set=utf8mb4）：
--   服务连的是 Docker MySQL（127.0.0.1:3307），必须打 3307，别打成默认的 3306 本机实例：
--   mysql -h127.0.0.1 -P3307 -uroot -p123456 --default-character-set=utf8mb4 -D e_commerce_db < docs/sql/order-index-migrate.sql
--
-- 幂等说明：MySQL 8.0 无 ADD INDEX IF NOT EXISTS，这里用存储过程先查 information_schema.STATISTICS
--          判断索引是否已存在，不存在才 ADD INDEX，重复执行不报错。

-- 存储过程：按需加索引（先判存在）
DELIMITER $$
DROP PROCEDURE IF EXISTS add_index_if_not_exists$$
CREATE PROCEDURE add_index_if_not_exists(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_def VARCHAR(255))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD INDEX `', p_index, '` ', p_def);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL add_index_if_not_exists('orders', 'idx_orders_status_created', '(order_status, created_at)');

DROP PROCEDURE IF EXISTS add_index_if_not_exists;

-- 自检：索引应能查到（列出索引名 + 列顺序）
SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'orders'
   AND INDEX_NAME = 'idx_orders_status_created'
 GROUP BY TABLE_NAME, INDEX_NAME
 ORDER BY TABLE_NAME, INDEX_NAME;
