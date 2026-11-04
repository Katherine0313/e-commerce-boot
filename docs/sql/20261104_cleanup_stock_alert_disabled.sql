-- 清理「未开启预警」的库存配置，释放被占用的编号
-- 背景：stock_alert.id 就是后台库存列表里的编号，历史上关闭预警时只把 alert_enabled 改成 0，
-- 记录仍留在表里，导致没开预警的商品也显示编号。
-- 新版 AdminStockServiceImpl 在 alertEnabled=0 时直接删除记录，本脚本补齐历史数据。

DELETE FROM stock_alert WHERE alert_enabled = 0;

-- 重新排号：把剩余记录的编号压实成 1..N，避免出现 2、4、7 这种断号
SET @n = 0;
UPDATE stock_alert SET id = (@n := @n + 1) ORDER BY id;
ALTER TABLE stock_alert AUTO_INCREMENT = 1;
