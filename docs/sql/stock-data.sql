-- 库存预警数据迁移（stock 表，product-service）
-- 数据来源：e-commerce/server/sqlite/database.sqlite（模板店 shopId=1）。
-- 执行方式：mysql -u e_commerce_db -p --default-character-set=utf8mb4 e_commerce_db < docs/sql/stock-data.sql
-- 说明：幂等可重复执行（INSERT IGNORE）；时间戳 INTEGER(Unix秒) → DATETIME（FROM_UNIXTIME，与 product.sql 一致）；
--       stock_status 列（旧表全空、代码已废弃改为实时计算）不迁移。

INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (1, 1, 1, 0, NULL, NULL, FROM_UNIXTIME(1788087032), NULL);
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (2, 1, 11, 1, 60, 1000, FROM_UNIXTIME(1788088234), FROM_UNIXTIME(1788417364));
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (3, 1, 32, 0, NULL, NULL, FROM_UNIXTIME(1788088235), FROM_UNIXTIME(1788089692));
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (4, 1, 27, 1, 50, 2000, FROM_UNIXTIME(1788088236), FROM_UNIXTIME(1788448801));
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (5, 1, 2, 0, NULL, NULL, FROM_UNIXTIME(1788088248), FROM_UNIXTIME(1788090241));
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (6, 1, 5, 0, NULL, NULL, FROM_UNIXTIME(1788088419), FROM_UNIXTIME(1788088419));
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (7, 1, 31, 1, 80, 800, FROM_UNIXTIME(1788088468), FROM_UNIXTIME(1788418973));
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (8, 1, 12, 1, 70, 900, FROM_UNIXTIME(1788088469), FROM_UNIXTIME(1788418662));
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (9, 1, 9, 1, 14, 560, FROM_UNIXTIME(1788088470), FROM_UNIXTIME(1788272899));
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (10, 1, 15, 1, 30, 450, FROM_UNIXTIME(1788096949), FROM_UNIXTIME(1788272926));
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (11, 1, 13, 1, 10, 100, FROM_UNIXTIME(1788097041), FROM_UNIXTIME(1788257539));
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (12, 1, 10, 0, NULL, NULL, FROM_UNIXTIME(1788097078), NULL);
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (13, 1, 14, 0, NULL, NULL, FROM_UNIXTIME(1788097209), FROM_UNIXTIME(1789780282));
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (14, 1, 22, 0, NULL, NULL, FROM_UNIXTIME(1788097221), FROM_UNIXTIME(1789780275));
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (15, 1, 16, 0, NULL, NULL, FROM_UNIXTIME(1788097227), FROM_UNIXTIME(1788415471));
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (16, 1, 3, 0, NULL, NULL, FROM_UNIXTIME(1788254698), NULL);
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (17, 1, 28, 0, NULL, NULL, FROM_UNIXTIME(1788256445), NULL);
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (18, 1, 21, 1, 50, 1000, FROM_UNIXTIME(1788256669), FROM_UNIXTIME(1788415311));
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (19, 1, 24, 1, 10, 500, FROM_UNIXTIME(1788257556), FROM_UNIXTIME(1788257562));
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (20, 1, 17, 1, 5, 600, FROM_UNIXTIME(1788257573), FROM_UNIXTIME(1788257582));
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (21, 1, 23, 1, 6, 500, FROM_UNIXTIME(1788258602), FROM_UNIXTIME(1788258611));
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (22, 1, 6, 0, NULL, NULL, FROM_UNIXTIME(1788258817), NULL);
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (23, 1, 25, 0, NULL, NULL, FROM_UNIXTIME(1788258994), NULL);
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (24, 1, 4, 1, 10, 400, FROM_UNIXTIME(1788259196), FROM_UNIXTIME(1788259205));
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (25, 1, 8, 1, 20, 700, FROM_UNIXTIME(1788259490), FROM_UNIXTIME(1788259500));
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (26, 1, 7, 1, 10, 500, FROM_UNIXTIME(1788259529), FROM_UNIXTIME(1788259535));
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (27, 1, 18, 1, 20, 900, FROM_UNIXTIME(1788260455), FROM_UNIXTIME(1788260464));
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (28, 1, 29, 1, 12, 800, FROM_UNIXTIME(1788260474), FROM_UNIXTIME(1788260536));
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (29, 1, 30, 0, NULL, NULL, FROM_UNIXTIME(1788416543), NULL);
INSERT IGNORE INTO `stock` (`id`, `shop_id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (30, 1, 26, 1, 70, 800, FROM_UNIXTIME(1788443759), FROM_UNIXTIME(1788443870));
