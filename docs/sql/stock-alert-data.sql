-- 库存预警配置数据（stock_alert 表，product-service）
-- 数据来源：本地 e_commerce_db.stock_alert 导出（2026-10-30）
-- 执行方式：mysql -u e_commerce_db -p --default-character-set=utf8mb4 e_commerce_db < docs/sql/stock-alert-data.sql
-- 说明：幂等可重复执行（INSERT IGNORE）；当前库存数量仍在 products.stock，本表只存预警配置。

INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (1, 1, 0, NULL, NULL, '2026-08-30 18:50:32', NULL);
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (2, 11, 1, 60, 1000, '2026-08-30 19:10:34', '2026-09-03 14:36:04');
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (3, 32, 0, NULL, NULL, '2026-08-30 19:10:35', '2026-08-30 19:34:52');
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (4, 27, 1, 50, 2000, '2026-08-30 19:10:36', '2026-09-03 23:20:01');
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (5, 2, 0, NULL, NULL, '2026-08-30 19:10:48', '2026-08-30 19:44:01');
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (6, 5, 0, NULL, NULL, '2026-08-30 19:13:39', '2026-08-30 19:13:39');
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (7, 31, 1, 80, 900, '2026-08-30 19:14:28', '2026-10-20 13:18:34');
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (8, 12, 1, 70, 900, '2026-08-30 19:14:29', '2026-09-03 14:57:42');
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (9, 9, 1, 14, 560, '2026-08-30 19:14:30', '2026-09-01 22:28:19');
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (10, 15, 1, 30, 450, '2026-08-30 21:35:49', '2026-09-01 22:28:46');
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (11, 13, 1, 10, 100, '2026-08-30 21:37:21', '2026-09-01 18:12:19');
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (12, 10, 0, NULL, NULL, '2026-08-30 21:37:58', NULL);
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (13, 14, 0, NULL, NULL, '2026-08-30 21:40:09', '2026-09-19 09:11:22');
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (14, 22, 0, NULL, NULL, '2026-08-30 21:40:21', '2026-09-19 09:11:15');
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (15, 16, 0, NULL, NULL, '2026-08-30 21:40:27', '2026-09-03 14:04:31');
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (16, 3, 0, NULL, NULL, '2026-09-01 17:24:58', NULL);
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (17, 28, 0, NULL, NULL, '2026-09-01 17:54:05', NULL);
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (18, 21, 1, 50, 1000, '2026-09-01 17:57:49', '2026-09-03 14:01:51');
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (19, 24, 1, 10, 500, '2026-09-01 18:12:36', '2026-09-01 18:12:42');
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (20, 17, 1, 5, 600, '2026-09-01 18:12:53', '2026-09-01 18:13:02');
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (21, 23, 1, 6, 500, '2026-09-01 18:30:02', '2026-09-01 18:30:11');
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (22, 6, 0, NULL, NULL, '2026-09-01 18:33:37', NULL);
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (23, 25, 0, NULL, NULL, '2026-09-01 18:36:34', NULL);
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (24, 4, 1, 10, 400, '2026-09-01 18:39:56', '2026-09-01 18:40:05');
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (25, 8, 1, 20, 700, '2026-09-01 18:44:50', '2026-09-01 18:45:00');
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (26, 7, 1, 10, 500, '2026-09-01 18:45:29', '2026-09-01 18:45:35');
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (27, 18, 1, 20, 900, '2026-09-01 19:00:55', '2026-09-01 19:01:04');
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (28, 29, 1, 12, 800, '2026-09-01 19:01:14', '2026-09-01 19:02:16');
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (29, 30, 0, NULL, NULL, '2026-09-03 14:22:23', NULL);
INSERT IGNORE INTO `stock_alert` (`id`, `product_id`, `alert_enabled`, `min_stock`, `max_stock`, `create_time`, `update_time`) VALUES (30, 26, 1, 70, 800, '2026-09-03 21:55:59', '2026-09-03 21:57:50');
