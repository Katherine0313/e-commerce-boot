-- 批量设置所有在售 SKU 预警阈值：最低 50、最高 500
UPDATE product_sku SET min_stock = 50, max_stock = 500, update_time = NOW() WHERE status = 1;
-- 验证
SELECT COUNT(*) AS affected FROM product_sku WHERE status = 1 AND min_stock = 50 AND max_stock = 500;
