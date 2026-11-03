-- ============================================================================
-- 发货巡检 SQL（只读，运维手工跑）
--
-- 用途：查「订单已发货超过 7 天，但订单还没完成」的订单 —— 说明 7 天自动完成
--       任务（findExpiredShipped → autoConfirm）没跑或没生效，需要人工介入。
--
-- 执行方式：
--   mysql -u root -p --default-character-set=utf8mb4 e_commerce_db < docs/sql/shipment/03-delivery-inspect.sql
--
-- 说明：只读查询，不改任何数据。
-- ============================================================================

-- 已发货超 7 天但订单未完成（7 天自动任务没跑）
SELECT o.id,
       o.order_no,
       o.order_status,
       o.ship_at,
       o.finish_at,
       o.sign_status,
       d.package_no,
       d.delivery_time,
       d.express_name,
       d.tracking_number
FROM orders o
         JOIN order_delivery d ON d.order_id = o.id AND d.is_deleted = 0
WHERE o.is_deleted = 0
  AND o.order_status = 4
  AND o.ship_at < DATE_SUB(NOW(), INTERVAL 7 DAY);
