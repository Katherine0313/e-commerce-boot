SELECT '=== 三张新表 ===' AS x;
SHOW TABLES LIKE 'stock_%';

SELECT '=== product_sku 新字段 ===' AS x;
SHOW COLUMNS FROM product_sku WHERE Field IN ('min_stock','max_stock','update_time','stock','sku_code','status');

SELECT '=== 迁移统计 ===' AS x;
SELECT COUNT(*) AS total_sku, SUM(CASE WHEN min_stock > 0 THEN 1 ELSE 0 END) AS alert_sku FROM product_sku;

SELECT '=== stock_alert 旧表数据量 ===' AS x;
SELECT COUNT(*) AS cnt FROM stock_alert;
