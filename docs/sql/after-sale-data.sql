-- 售后域数据迁移脚本（aftersale-service）
-- 数据来源：e-commerce/server/sqlite/database.sqlite 模板店
-- 执行方式：mysql -u root -p --default-character-set=utf8mb4 e_commerce_db < docs/sql/after-sale-data.sql
-- 说明：INSERT IGNORE 幂等；时间戳 INTEGER（Unix 秒）用 FROM_UNIXTIME 内联转换。
-- 依赖：先执行 docs/sql/order.sql（orders）与售后五张建表语句；orders 数据需已迁移（order-data.sql）。
--
-- 迁移约定（延续 order-data.sql）：
--   1. id 保留原值（含 9104/9106/9107 号段跳号），因为四张子表都靠 after_sale_id 关联，重映射风险大。
--   2. after_sale_only_refunds 丢弃 11 条孤儿（after_sale_id=23 与 9108~9117 的售后主单已在源库被删除，
--      直接插入会违反外键 fk_after_sale_only_refunds_after_sale）。
--   3. after_sale_items 有一条悬空引用（id=39 的 order_item_id=74 / product_id=41，对应订单迁移时被丢弃的
--      孤儿订单项），该列无外键约束，保留原值以维持售后历史完整。

-- ==================== 售后单 ====================
INSERT IGNORE INTO `after_sales` (`id`, `order_id`, `after_sale_no`, `order_no`, `after_sale_type`, `status`, `created_at`, `updated_at`, `is_deleted`, `reason`, `remark`, `images`, `merchant_remark`) VALUES
(1, 74, 'AS20260814125252', 'ORD20260812151113', 'return_refund', 'refunded', FROM_UNIXTIME(1786683172), FROM_UNIXTIME(1787729862), 1, 'seven_days', '不想要了', '["/image/upload/19b059e5-79c7-425f-8359-47c04789c4b5.webp"]', NULL),
(2, 79, 'AS20260814171008', 'ORD20260814141635', 'only_refund', 'reviewing', FROM_UNIXTIME(1786698608), FROM_UNIXTIME(1787913559), 1, 'cancel', '希望尽快退款', '[]', NULL),
(7, 22, 'AS20260814172436', 'ORD20260728000000', 'return_refund', 'returning', FROM_UNIXTIME(1786699476), FROM_UNIXTIME(1787732442), 0, 'seven_days', '想买别的', '["/image/upload/64f92b2f-3137-40ad-a5d2-2179589d068c.webp"]', ''),
(8, 77, 'AS20260814173217', 'ORD20260812153717', 'return_refund', 'returning', FROM_UNIXTIME(1786699937), FROM_UNIXTIME(1787732371), 0, 'quality', '坏了', '["/image/upload/15204e22-b14e-4beb-8ddb-724702cd3e34.webp"]', ''),
(9, 17, 'AS20260724002417', 'ORD20260724000000', 'only_refund', 'returning', FROM_UNIXTIME(1784823857), FROM_UNIXTIME(1787731415), 0, 'cancel', '希望尽快退款', '[]', ''),
(10, 25, 'AS20260721060932', 'ORD20260721060000', 'only_refund', 'reviewing', FROM_UNIXTIME(1784585372), FROM_UNIXTIME(1787001221), 0, 'cancel', '希望尽快退款', '[]', ''),
(11, 26, 'AS20260723183709', 'ORD20260723180000', 'only_refund', 'refunding', FROM_UNIXTIME(1784803029), FROM_UNIXTIME(1787731388), 0, 'cancel', '希望尽快退款', '[]', ''),
(12, 31, 'AS20260801202852', 'ORD20260801202852', 'only_refund', 'reviewing', FROM_UNIXTIME(1785587332), FROM_UNIXTIME(1787913658), 1, 'cancel', '希望尽快退款', '[]', ''),
(13, 45, 'AS20260731200654', 'ORD20260731200654', 'only_refund', 'reviewing', FROM_UNIXTIME(1785499614), FROM_UNIXTIME(1787001221), 0, 'cancel', '希望尽快退款', '[]', ''),
(14, 64, 'AS20260812090000', 'ORD20260812090008', 'only_refund', 'reviewing', FROM_UNIXTIME(1786496400), FROM_UNIXTIME(1787001221), 0, 'cancel', '希望尽快退款', '[]', ''),
(15, 68, 'AS20260812134405', 'ORD20260812134405', 'only_refund', 'reviewing', FROM_UNIXTIME(1786513445), FROM_UNIXTIME(1787001221), 0, 'cancel', '希望尽快退款', '[]', ''),
(16, 72, 'AS20260812150902', 'ORD20260812150902', 'only_refund', 'reviewing', FROM_UNIXTIME(1786518542), FROM_UNIXTIME(1787001221), 0, 'cancel', '希望尽快退款', '[]', ''),
(17, 76, 'AS20260812153621', 'ORD20260812153621', 'only_refund', 'reviewing', FROM_UNIXTIME(1786520181), FROM_UNIXTIME(1787001221), 0, 'cancel', '希望尽快退款', '[]', ''),
(18, 78, 'AS20260812171253', 'ORD20260812171253', 'only_refund', 'reviewing', FROM_UNIXTIME(1786525973), FROM_UNIXTIME(1787001221), 0, 'cancel', '希望尽快退款', '[]', ''),
(19, 75, 'AS20260814181515', 'ORD20260812153520', 'only_refund', 'refunded', FROM_UNIXTIME(1786702515), FROM_UNIXTIME(1787035155), 0, 'cancel', '希望尽快退款', '[]', ''),
(20, 80, 'AS20260817104407', 'ORD20260817104344', 'only_refund', 'refund_confirming', FROM_UNIXTIME(1786934647), FROM_UNIXTIME(1787034650), 0, 'cancel', '希望尽快退款', '[]', ''),
(21, 81, 'AS20260817105824', 'ORD20260817105705', 'only_refund', 'refunding', FROM_UNIXTIME(1786935504), FROM_UNIXTIME(1789134306), 0, 'seven_days', '不是很想要这个款式', '[]', ''),
(22, 82, 'AS20260817110317', 'ORD20260817110011', 'only_refund', 'refunded', FROM_UNIXTIME(1786935797), FROM_UNIXTIME(1787913352), 1, 'seven_days', '想要别的款式和颜色', '[]', ''),
(24, 59, 'AS20260817194614', 'ORD20260812090003', 'only_refund', 'refunded', FROM_UNIXTIME(1786967174), FROM_UNIXTIME(1787913178), 1, 'cancel', '希望尽快退款', '[]', ''),
(25, 60, 'AS20260817194745', 'ORD20260812090004', 'only_refund', 'refunding', FROM_UNIXTIME(1786967265), FROM_UNIXTIME(1788278631), 1, 'cancel', '希望尽快退款', '[]', ''),
(28, 57, 'AS20260820174741', 'ORD20260812090001', 'return_refund', 'refunded', FROM_UNIXTIME(1787219261), FROM_UNIXTIME(1787727914), 0, 'wrong_goods', '', '[]', ''),
(29, 42, 'AS20260822204033', 'ORD20260724114515', 'return_refund', 'reviewing', FROM_UNIXTIME(1787402433), FROM_UNIXTIME(1787731919), 0, 'wrong_goods', '码数发错了', '[]', ''),
(30, 58, 'AS20260822210458', 'ORD20260812090002', 'return_refund', 'reviewing', FROM_UNIXTIME(1787403898), FROM_UNIXTIME(1787403898), 0, 'wrong_goods', '发少了一件', '[]', ''),
(31, 43, 'AS20260822210917', 'ORD20260722145228', 'return_refund', 'refunded', FROM_UNIXTIME(1787404157), FROM_UNIXTIME(1787404401), 0, 'seven_days', '', '[]', ''),
(33, 61, 'AS20260826144843', 'ORD20260812090005', 'exchange', 'exchanged', FROM_UNIXTIME(1787726923), FROM_UNIXTIME(1787910378), 1, 'quality', '', '[]', ''),
(9104, 62, 'AS20260826152032', 'ORD20260812090006', 'exchange', 'exchanged', FROM_UNIXTIME(1787728832), FROM_UNIXTIME(1787730918), 0, 'seven_days', '', '[]', NULL),
(9106, 70, 'AS20260826160136', 'ORD20260812145435', 'exchange', 'reviewing', FROM_UNIXTIME(1787731296), FROM_UNIXTIME(1787733859), 0, 'seven_days', '', '[]', ''),
(9107, 63, 'AS20260826163354', 'ORD20260812090007', 'exchange', 'returning', FROM_UNIXTIME(1787733234), FROM_UNIXTIME(1787734283), 0, 'rejected', '', '[]', '');

-- ==================== 售后明细 ====================
INSERT IGNORE INTO `after_sale_items` (`id`, `after_sale_id`, `order_item_id`, `product_id`, `product_name`, `image`, `sales_price`, `quantity`, `refund_amount`, `insurance_detail`, `insurance_fee`, `created_at`, `updated_at`, `is_deleted`) VALUES
(1, 1, 101, 15, '苹果iPad Pro 12.9', '/image/upload/15.webp', 8999.0, 1, 8999.0, '[]', 0.0, FROM_UNIXTIME(1786683172), FROM_UNIXTIME(1786683172), 0),
(2, 1, 102, 20, '索尼WH-CH720N', '/image/upload/20.webp', 2199.0, 1, 2199.0, '[]', 0.0, FROM_UNIXTIME(1786683172), FROM_UNIXTIME(1786683172), 0),
(3, 2, 109, 3, '华为Mate 60 Pro', '/image/upload/3.webp', 6999.0, 2, 13998.0, '[]', 0.0, FROM_UNIXTIME(1786698608), FROM_UNIXTIME(1787913559), 1),
(4, 2, 110, 2, '华为Pura 70 Ultra', '/image/upload/2.webp', 7999.0, 1, 7999.0, '[]', 0.0, FROM_UNIXTIME(1786698608), FROM_UNIXTIME(1787913559), 1),
(5, 7, 40, 26, '苹果Watch Ultra', '/image/upload/26.webp', 6499.0, 1, 6499.0, '[]', 0.0, FROM_UNIXTIME(1786699476), FROM_UNIXTIME(1786699476), 0),
(6, 7, 41, 7, '小米12', '/image/upload/7.webp', 4299.0, 2, 8598.0, '[]', 0.0, FROM_UNIXTIME(1786699476), FROM_UNIXTIME(1786699476), 0),
(7, 8, 106, 9, '苹果MacBook Pro', '/image/upload/530c8603-9b9d-4b5b-b3e7-31f6ecdb7ea9.webp', 14999.0, 1, 14999.0, '[]', 0.0, FROM_UNIXTIME(1786699937), FROM_UNIXTIME(1786699937), 0),
(8, 9, 30, 2, '华为Pura 70 Ultra', '/image/upload/2.webp', 7999.0, 1, 7999.0, '[]', 0.0, FROM_UNIXTIME(1784823857), FROM_UNIXTIME(1784823857), 0),
(9, 9, 31, 10, '苹果MacBook Pro 14', '/image/upload/10.webp', 8499.0, 1, 8499.0, '[]', 0.0, FROM_UNIXTIME(1784823857), FROM_UNIXTIME(1784823857), 0),
(10, 10, 47, 4, '小米14 Pro', '/image/upload/4.webp', 4999.0, 1, 4999.0, '[]', 0.0, FROM_UNIXTIME(1784585372), FROM_UNIXTIME(1784585372), 0),
(11, 11, 48, 2, '华为Pura 70 Ultra', '/image/upload/2.webp', 7999.0, 1, 7999.0, '[]', 0.0, FROM_UNIXTIME(1784803029), FROM_UNIXTIME(1784803029), 0),
(12, 11, 49, 7, '小米12', '/image/upload/7.webp', 4299.0, 1, 4299.0, '[]', 0.0, FROM_UNIXTIME(1784803029), FROM_UNIXTIME(1784803029), 0),
(13, 12, 56, 26, '苹果Watch Ultra', '/image/upload/26.webp', 6499.0, 1, 6499.0, '[]', 0.0, FROM_UNIXTIME(1785587332), FROM_UNIXTIME(1787913658), 1),
(14, 13, 77, 2, '华为Pura 70 Ultra', '/image/upload/2.webp', 8999.0, 1, 8999.0, '[]', 0.0, FROM_UNIXTIME(1785499614), FROM_UNIXTIME(1785499614), 0),
(15, 14, 89, 14, '苹果iPad Air', '/image/upload/14.webp', 4599.0, 1, 4599.0, '[]', 0.0, FROM_UNIXTIME(1786496400), FROM_UNIXTIME(1786496400), 0),
(16, 15, 94, 4, '小米14 Pro', '/image/upload/4.webp', 4999.0, 1, 4999.0, '[]', 0.0, FROM_UNIXTIME(1786513445), FROM_UNIXTIME(1786513445), 0),
(17, 16, 99, 7, '小米12', '/image/upload/7.webp', 4299.0, 1, 4299.0, '[]', 0.0, FROM_UNIXTIME(1786518542), FROM_UNIXTIME(1786518542), 0),
(18, 17, 105, 8, '苹果iPhone 15', '/image/upload/8.webp', 5999.0, 1, 5999.0, '[]', 0.0, FROM_UNIXTIME(1786520181), FROM_UNIXTIME(1786520181), 0),
(19, 18, 107, 3, '华为Mate 60 Pro', '/image/upload/3.webp', 6999.0, 1, 6999.0, '[]', 0.0, FROM_UNIXTIME(1786525973), FROM_UNIXTIME(1786525973), 0),
(20, 18, 108, 9, '苹果MacBook Pro', '/image/upload/530c8603-9b9d-4b5b-b3e7-31f6ecdb7ea9.webp', 14999.0, 1, 14999.0, '[]', 0.0, FROM_UNIXTIME(1786525973), FROM_UNIXTIME(1786525973), 0),
(21, 19, 103, 1, '苹果iPhone 15 Pro', '/image/upload/1.webp', 9999.0, 1, 9999.0, '[]', 0.0, FROM_UNIXTIME(1786702515), FROM_UNIXTIME(1786702515), 0),
(22, 19, 104, 2, '华为Pura 70 Ultra', '/image/upload/2.webp', 7999.0, 1, 7999.0, '[]', 0.0, FROM_UNIXTIME(1786702515), FROM_UNIXTIME(1786702515), 0),
(23, 20, 111, 4, '小米14 Pro', '/image/upload/4.webp', 4999.0, 1, 4999.0, '[]', 0.0, FROM_UNIXTIME(1786934647), FROM_UNIXTIME(1786934647), 0),
(24, 20, 112, 7, '小米12', '/image/upload/7.webp', 4299.0, 1, 4361.99, '[{"code":"warranty","name":"全面保修","fee":42.99},{"code":"battery","name":"电池无忧","fee":20}]', 62.99, FROM_UNIXTIME(1786934647), FROM_UNIXTIME(1786934647), 0),
(25, 20, 113, 2, '华为Pura 70 Ultra', '/image/upload/2.webp', 7999.0, 1, 7999.0, '[]', 0.0, FROM_UNIXTIME(1786934647), FROM_UNIXTIME(1786934647), 0),
(26, 20, 114, 9, '苹果MacBook Pro', '/image/upload/530c8603-9b9d-4b5b-b3e7-31f6ecdb7ea9.webp', 14999.0, 1, 14999.0, '[]', 0.0, FROM_UNIXTIME(1786934647), FROM_UNIXTIME(1786934647), 0),
(27, 21, 115, 2, '华为Pura 70 Ultra', '/image/upload/2.webp', 7999.0, 1, 7999.0, '[]', 0.0, FROM_UNIXTIME(1786935504), FROM_UNIXTIME(1786935504), 0),
(28, 21, 116, 32, '尼康Zf', '/image/upload/32.webp', 11799.0, 1, 11799.0, '[]', 0.0, FROM_UNIXTIME(1786935504), FROM_UNIXTIME(1786935504), 0),
(29, 22, 117, 9, '苹果MacBook Pro', '/image/upload/530c8603-9b9d-4b5b-b3e7-31f6ecdb7ea9.webp', 14999.0, 1, 15019.0, '[{"code":"battery","name":"电池无忧","fee":20}]', 20.0, FROM_UNIXTIME(1786935797), FROM_UNIXTIME(1787913352), 1),
(30, 22, 118, 24, '苹果AirPods 4', '/image/upload/24.webp', 499.0, 1, 508.98, '[{"code":"accident","name":"一年意外保","fee":9.98}]', 9.98, FROM_UNIXTIME(1786935797), FROM_UNIXTIME(1787913352), 1),
(32, 24, 82, 3, '华为Mate 60 Pro', '/image/upload/3.webp', 6999.0, 1, 6999.0, '[]', 0.0, FROM_UNIXTIME(1786967174), FROM_UNIXTIME(1787913178), 1),
(33, 24, 83, 18, '苹果AirPods Pro', '/image/upload/18.webp', 1899.0, 1, 1899.0, '[]', 0.0, FROM_UNIXTIME(1786967174), FROM_UNIXTIME(1787913178), 1),
(34, 25, 84, 5, '小米15 Pro', '/image/upload/5.webp', 5299.0, 1, 5299.0, '[]', 0.0, FROM_UNIXTIME(1786967265), FROM_UNIXTIME(1788278631), 1),
(35, 28, 80, 1, '苹果iPhone 15 Pro', '/image/upload/1.webp', 7999.0, 1, 7999.0, '[]', 0.0, FROM_UNIXTIME(1787219261), FROM_UNIXTIME(1787219261), 0),
(36, 29, 72, 24, '苹果AirPods Pro', '/image/upload/24.webp', 1899.0, 1, 1899.0, '[]', 0.0, FROM_UNIXTIME(1787402433), FROM_UNIXTIME(1787402433), 0),
(37, 29, 73, 28, '苹果Watch Series 9', '/image/upload/28.webp', 3199.0, 1, 3199.0, '[]', 0.0, FROM_UNIXTIME(1787402433), FROM_UNIXTIME(1787402433), 0),
(38, 30, 81, 9, '苹果MacBook Pro', '/image/upload/9.webp', 14999.0, 1, 14999.0, '[]', 0.0, FROM_UNIXTIME(1787403898), FROM_UNIXTIME(1787403898), 0),
(39, 31, 74, 41, '尼康Z8', '/image/upload/31.webp', 27999.0, 1, 27999.0, '[]', 0.0, FROM_UNIXTIME(1787404157), FROM_UNIXTIME(1787404157), 0),
(40, 33, 85, 6, '小米15', '/image/upload/6.webp', 3999.0, 1, 3999.0, '[]', 0.0, FROM_UNIXTIME(1787726923), FROM_UNIXTIME(1787910378), 1),
(41, 9104, 86, 12, '苹果MacBook Pro 16', '/image/upload/12.webp', 19999.0, 1, 19999.0, '[]', 0.0, FROM_UNIXTIME(1787728832), FROM_UNIXTIME(1787730918), 1),
(42, 9104, 87, 19, '索尼WH-1000XM5', '/image/upload/19.webp', 2499.0, 1, 2499.0, '[]', 0.0, FROM_UNIXTIME(1787728832), FROM_UNIXTIME(1787730918), 1),
(44, 9106, 96, 4, '小米14 Pro', '/image/upload/4.webp', 4999.0, 1, 4999.0, '[]', 0.0, FROM_UNIXTIME(1787731296), FROM_UNIXTIME(1787731296), 0),
(45, 9107, 88, 15, '苹果iPad Pro 12.9', '/image/upload/15.webp', 8999.0, 1, 8999.0, '[]', 0.0, FROM_UNIXTIME(1787733234), FROM_UNIXTIME(1787733234), 0);

-- ==================== 退货信息 ====================
INSERT IGNORE INTO `after_sale_returns` (`id`, `after_sale_id`, `return_way`, `return_time_slot`, `return_at`, `review_at`, `rejected_at`, `refund_at`, `refunded_at`, `created_at`, `updated_at`, `is_deleted`) VALUES
(4, 8, 'pickup', 'morning', FROM_UNIXTIME(1787402265), FROM_UNIXTIME(1787732371), NULL, NULL, NULL, FROM_UNIXTIME(1787402265), FROM_UNIXTIME(1787732371), 0),
(5, 31, 'pickup', 'morning', FROM_UNIXTIME(1787404280), FROM_UNIXTIME(1787404196), NULL, FROM_UNIXTIME(1787404362), FROM_UNIXTIME(1787404401), FROM_UNIXTIME(1787404280), FROM_UNIXTIME(1787404280), 0),
(7, 1, 'pickup', 'afternoon', FROM_UNIXTIME(1787728984), FROM_UNIXTIME(1787728979), NULL, NULL, FROM_UNIXTIME(1787729862), FROM_UNIXTIME(1787728984), FROM_UNIXTIME(1787728984), 0),
(8, 7, NULL, NULL, NULL, FROM_UNIXTIME(1787732442), NULL, NULL, NULL, FROM_UNIXTIME(1786699476), FROM_UNIXTIME(1787732442), 0),
(9, 29, NULL, NULL, NULL, FROM_UNIXTIME(1787731919), NULL, NULL, NULL, FROM_UNIXTIME(1787402433), FROM_UNIXTIME(1787731919), 0),
(10, 30, NULL, NULL, NULL, NULL, NULL, NULL, NULL, FROM_UNIXTIME(1787403898), FROM_UNIXTIME(1787403898), 0),
(11, 28, NULL, NULL, NULL, NULL, NULL, NULL, FROM_UNIXTIME(1787727914), FROM_UNIXTIME(1787219261), FROM_UNIXTIME(1787727914), 0);

-- ==================== 仅退款信息 ====================
-- 已丢弃 11 条孤儿（after_sale_id=23 与 9108~9117 的售后主单不存在，见文件头说明）
INSERT IGNORE INTO `after_sale_only_refunds` (`id`, `after_sale_id`, `review_at`, `rejected_at`, `refund_at`, `refunded_at`, `created_at`, `updated_at`, `is_deleted`) VALUES
(1, 2, NULL, NULL, NULL, NULL, FROM_UNIXTIME(1786698608), FROM_UNIXTIME(1787913559), 1),
(2, 9, FROM_UNIXTIME(1787731415), NULL, NULL, NULL, FROM_UNIXTIME(1784823857), FROM_UNIXTIME(1787731415), 0),
(3, 10, NULL, NULL, NULL, NULL, FROM_UNIXTIME(1784585372), FROM_UNIXTIME(1787001221), 0),
(4, 11, FROM_UNIXTIME(1787731388), NULL, NULL, NULL, FROM_UNIXTIME(1784803029), FROM_UNIXTIME(1787731388), 0),
(5, 12, NULL, NULL, NULL, NULL, FROM_UNIXTIME(1785587332), FROM_UNIXTIME(1787913658), 1),
(6, 13, NULL, NULL, NULL, NULL, FROM_UNIXTIME(1785499614), FROM_UNIXTIME(1787001221), 0),
(7, 14, NULL, NULL, NULL, NULL, FROM_UNIXTIME(1786496400), FROM_UNIXTIME(1787001221), 0),
(8, 15, NULL, NULL, NULL, NULL, FROM_UNIXTIME(1786513445), FROM_UNIXTIME(1787001221), 0),
(9, 16, NULL, NULL, NULL, NULL, FROM_UNIXTIME(1786518542), FROM_UNIXTIME(1787001221), 0),
(10, 17, NULL, NULL, NULL, NULL, FROM_UNIXTIME(1786520181), FROM_UNIXTIME(1787001221), 0),
(11, 18, NULL, NULL, NULL, NULL, FROM_UNIXTIME(1786525973), FROM_UNIXTIME(1787001221), 0),
(12, 19, FROM_UNIXTIME(1787035009), NULL, FROM_UNIXTIME(1787035087), FROM_UNIXTIME(1787035155), FROM_UNIXTIME(1786702515), FROM_UNIXTIME(1787035155), 0),
(13, 20, FROM_UNIXTIME(1787034511), NULL, FROM_UNIXTIME(1787034650), NULL, FROM_UNIXTIME(1786934647), FROM_UNIXTIME(1787034650), 0),
(14, 21, FROM_UNIXTIME(1789134306), NULL, FROM_UNIXTIME(1787034253), FROM_UNIXTIME(1787034366), FROM_UNIXTIME(1786935504), FROM_UNIXTIME(1789134306), 0),
(15, 22, FROM_UNIXTIME(1787033277), NULL, FROM_UNIXTIME(1787033295), FROM_UNIXTIME(1787033316), FROM_UNIXTIME(1786935797), FROM_UNIXTIME(1787913352), 1),
(17, 24, FROM_UNIXTIME(1787033100), NULL, NULL, FROM_UNIXTIME(1787033170), FROM_UNIXTIME(1786967174), FROM_UNIXTIME(1787913178), 1),
(18, 25, FROM_UNIXTIME(1787033771), NULL, NULL, NULL, FROM_UNIXTIME(1786967265), FROM_UNIXTIME(1788278631), 1);

-- ==================== 换货退货信息 ====================
INSERT IGNORE INTO `after_sale_exchange_returns` (`id`, `after_sale_id`, `return_way`, `return_time_slot`, `created_at`, `updated_at`, `review_at`, `return_at`, `exchange_at`, `exchanged_at`, `rejected_at`, `is_deleted`) VALUES
(6, 9104, 'self', NULL, FROM_UNIXTIME(1787728862), FROM_UNIXTIME(1787730918), FROM_UNIXTIME(1787729234), FROM_UNIXTIME(1787728862), FROM_UNIXTIME(1787729541), FROM_UNIXTIME(1787729673), NULL, 0),
(7, 33, 'self', NULL, FROM_UNIXTIME(1787726923), FROM_UNIXTIME(1787910378), FROM_UNIXTIME(1787734151), FROM_UNIXTIME(1787734157), FROM_UNIXTIME(1787734187), FROM_UNIXTIME(1787734204), NULL, 1),
(8, 9106, NULL, NULL, FROM_UNIXTIME(1787731296), FROM_UNIXTIME(1787733859), NULL, NULL, NULL, NULL, NULL, 0),
(9, 9107, NULL, NULL, FROM_UNIXTIME(1787733234), FROM_UNIXTIME(1787734283), FROM_UNIXTIME(1787734283), NULL, NULL, NULL, NULL, 0);
