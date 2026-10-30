-- =====================================================================
-- 10：手表下新增二级分类「机械普通手表」(503) + 10 款机械/石英表商品
-- 特点：传统指针表，无电子传感；机械表、石英表
-- 商品 id 127~136，product_code SEED-127~136，图片 /image/upload/{id}.webp
-- 可重复执行（INSERT IGNORE）
-- =====================================================================
SET NAMES utf8mb4;

-- 1. 二级分类（父级：5 手表）
INSERT IGNORE INTO categories (id, name, parent_id, sort_order, status, recommend, image)
VALUES (503, '机械普通手表', 5, 2, 1, 0, NULL);

-- 2. 商品
INSERT IGNORE INTO products
    (id, category_id, tag_id, name, sub_title, brand, title, image, body,
     sales_mode, listing_status, sales_price, market_price, cost_price, product_code,
     stock, weight, volume, specs, create_time, update_time)
VALUES
(127, 503, NULL, '天梭力洛克 T006 机械男表',
 '已售1200+ | 可开发票 | 经典百搭好评多', '天梭',
 '天梭力洛克 T006 机械男表 自动机械机芯 39.3mm精钢表壳 30米防水 80小时动力 官方正品 现货速发',
 '/image/upload/127.webp', NULL, 'spot', 1, 3200.00, 3499.00, 2400.00, 'SEED-127', 120, NULL, NULL,
 '[{"name":"机芯","value":"自动机械"},{"name":"表盘直径","value":"39.3mm"},{"name":"防水","value":"30米"},{"name":"表带材质","value":"精钢"},{"name":"动力储存","value":"80小时"}]',
 '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
(128, 503, NULL, '浪琴名匠 L2.628 机械男表',
 '可开发票 | 商务通勤皆宜 | 超6000人加购', '浪琴',
 '浪琴名匠 L2.628 机械男表 自动机械机芯 38.5mm真皮表带 蓝钢指针 64小时动力 官方正品 现货速发',
 '/image/upload/128.webp', NULL, 'spot', 1, 12800.00, 13999.00, 9800.00, 'SEED-128', 45, NULL, NULL,
 '[{"name":"机芯","value":"自动机械"},{"name":"表盘直径","value":"38.5mm"},{"name":"防水","value":"30米"},{"name":"表带材质","value":"真皮"},{"name":"动力储存","value":"64小时"}]',
 '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
(129, 503, NULL, '精工5号 SRPD55K1 自动机械表',
 '走时精准好评多 | 已售2500+ | 可开发票', '精工',
 '精工5号 SRPD55K1 自动机械表 自动上链 42.5mm不锈钢表带 100米防水 41小时动力 官方正品 现货速发',
 '/image/upload/129.webp', NULL, 'spot', 1, 1180.00, 1299.00, 820.00, 'SEED-129', 200, NULL, NULL,
 '[{"name":"机芯","value":"自动机械"},{"name":"表盘直径","value":"42.5mm"},{"name":"防水","value":"100米"},{"name":"表带材质","value":"不锈钢"},{"name":"动力储存","value":"41小时"}]',
 '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
(130, 503, NULL, '卡西欧EDIFICE EFV-100D 石英表',
 '已售900+ | 可开发票 | 走时精准好评多', '卡西欧',
 '卡西欧EDIFICE EFV-100D 石英表 石英机芯 42mm不锈钢表带 100米防水 日期显示 官方正品 现货速发',
 '/image/upload/130.webp', NULL, 'spot', 1, 620.00, 699.00, 410.00, 'SEED-130', 260, NULL, NULL,
 '[{"name":"机芯","value":"石英"},{"name":"表盘直径","value":"42mm"},{"name":"防水","value":"100米"},{"name":"表带材质","value":"不锈钢"},{"name":"功能","value":"日期显示"}]',
 '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
(131, 503, NULL, '美度贝伦赛丽 M8600 机械表',
 '可开发票 | 经典百搭好评多 | 超4000人加购', '美度',
 '美度贝伦赛丽 M8600 机械表 自动机械机芯 38mm真皮表带 50米防水 80小时动力 官方正品 现货速发',
 '/image/upload/131.webp', NULL, 'spot', 1, 5600.00, 6199.00, 4200.00, 'SEED-131', 80, NULL, NULL,
 '[{"name":"机芯","value":"自动机械"},{"name":"表盘直径","value":"38mm"},{"name":"防水","value":"50米"},{"name":"表带材质","value":"真皮"},{"name":"动力储存","value":"80小时"}]',
 '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
(132, 503, NULL, '汉米尔顿卡其野战 H69439931 机械表',
 '商务通勤皆宜 | 已售800+ | 可开发票', '汉米尔顿',
 '汉米尔顿卡其野战 H69439931 机械表 手动上链 38mm织物表带 50米防水 80小时动力 官方正品 现货速发',
 '/image/upload/132.webp', NULL, 'spot', 1, 4300.00, 4699.00, 3100.00, 'SEED-132', 95, NULL, NULL,
 '[{"name":"机芯","value":"手动机械"},{"name":"表盘直径","value":"38mm"},{"name":"防水","value":"50米"},{"name":"表带材质","value":"织物"},{"name":"动力储存","value":"80小时"}]',
 '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
(133, 503, NULL, '雪铁纳DS PH200M 潜水机械表',
 '已售600+ | 可开发票 | 商务通勤皆宜', '雪铁纳',
 '雪铁纳DS PH200M 潜水机械表 自动机械机芯 42.8mm橡胶表带 200米防水 80小时动力 官方正品 现货速发',
 '/image/upload/133.webp', NULL, 'spot', 1, 6200.00, 6799.00, 4500.00, 'SEED-133', 70, NULL, NULL,
 '[{"name":"机芯","value":"自动机械"},{"name":"表盘直径","value":"42.8mm"},{"name":"防水","value":"200米"},{"name":"表带材质","value":"橡胶"},{"name":"动力储存","value":"80小时"}]',
 '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
(134, 503, NULL, '飞亚达经典系列 GA802057 机械男表',
 '可开发票 | 走时精准好评多 | 超3000人加购', '飞亚达',
 '飞亚达经典系列 GA802057 机械男表 自动机械机芯 41mm真皮表带 50米防水 40小时动力 官方正品 现货速发',
 '/image/upload/134.webp', NULL, 'spot', 1, 2680.00, 2999.00, 1950.00, 'SEED-134', 130, NULL, NULL,
 '[{"name":"机芯","value":"自动机械"},{"name":"表盘直径","value":"41mm"},{"name":"防水","value":"50米"},{"name":"表带材质","value":"真皮"},{"name":"动力储存","value":"40小时"}]',
 '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
(135, 503, NULL, '西铁城NH8350-83L 自动机械表',
 '经典百搭好评多 | 已售1500+ | 可开发票', '西铁城',
 '西铁城NH8350-83L 自动机械表 自动上链 40mm不锈钢表带 50米防水 40小时动力 官方正品 现货速发',
 '/image/upload/135.webp', NULL, 'spot', 1, 1180.00, 1289.00, 800.00, 'SEED-135', 180, NULL, NULL,
 '[{"name":"机芯","value":"自动机械"},{"name":"表盘直径","value":"40mm"},{"name":"防水","value":"50米"},{"name":"表带材质","value":"不锈钢"},{"name":"动力储存","value":"40小时"}]',
 '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
(136, 503, NULL, '雷达晶萃 R30939103 自动机械男表',
 '已售400+ | 可开发票 | 商务通勤皆宜', '雷达',
 '雷达晶萃 R30939103 自动机械男表 38mm精钢表带 30米防水 蓝宝石镜面 官方正品 现货速发',
 '/image/upload/136.webp', NULL, 'spot', 1, 9800.00, 10999.00, 7400.00, 'SEED-136', 38, NULL, NULL,
 '[{"name":"机芯","value":"自动机械"},{"name":"表盘直径","value":"38mm"},{"name":"防水","value":"30米"},{"name":"表带材质","value":"精钢"},{"name":"镜面","value":"蓝宝石"}]',
 '2026-10-01 10:00:00', '2026-10-01 10:00:00');

-- 3. 验证
SELECT c.id, c.name, COUNT(p.id) AS cnt
FROM categories c LEFT JOIN products p ON p.category_id = c.id
WHERE c.parent_id = 5
GROUP BY c.id, c.name ORDER BY c.id;

SELECT COUNT(*) AS total_products FROM products;
SELECT COUNT(*) AS child_count FROM categories WHERE parent_id IS NOT NULL;
