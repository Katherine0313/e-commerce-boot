-- ============================================================
-- SKU 规格修正：按商品特性（名称关键词）重新生成
-- 2026-11-06
-- 适用：product_sku 已按粗分类生成过的库
-- 影响范围：分类 701 / 702 / 703 / 704 / 801 / 802 / 803 / 804
-- 兼容 MySQL 5.7 / 8.0
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 补充规格字典
-- ------------------------------------------------------------
INSERT INTO product_spec_group (id, category_id, name, sort, status, create_time, update_time) VALUES
  (6,  NULL, '轴体',       6,  1, NOW(), NOW()),
  (7,  NULL, '清晰度',     7,  1, NOW(), NOW()),
  (8,  NULL, '色温',       8,  1, NOW(), NOW()),
  (9,  NULL, '版本',       9,  1, NOW(), NOW()),
  (10, NULL, '电池容量',   10, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO product_spec_value (group_id, value, sort, create_time) VALUES
  (6,  '红轴',     1, NOW()),
  (6,  '青轴',     2, NOW()),
  (6,  '茶轴',     3, NOW()),
  (7,  '2K',       1, NOW()),
  (7,  '4K',       2, NOW()),
  (8,  '暖光',     1, NOW()),
  (8,  '正白',     2, NOW()),
  (8,  '彩光',     3, NOW()),
  (9,  '标准版',   1, NOW()),
  (9,  '水箱版',   2, NOW()),
  (10, '10000mAh', 1, NOW()),
  (10, '20000mAh', 2, NOW()),
  (2,  '1TB',      4, NOW()),
  (2,  '2TB',      5, NOW())
ON DUPLICATE KEY UPDATE sort = VALUES(sort);

-- ------------------------------------------------------------
-- 2. 先解绑 cart / order_items，再删旧 SKU
-- ------------------------------------------------------------
UPDATE cart c
JOIN products p ON p.id = c.product_id
SET c.sku_id = NULL, c.sku_code = NULL, c.spec_json = NULL
WHERE p.category_id IN (701, 702, 703, 704, 801, 802, 803, 804);

UPDATE order_items oi
JOIN products p ON p.id = oi.product_id
SET oi.sku_id = NULL, oi.sku_code = NULL, oi.spec_json = NULL
WHERE p.category_id IN (701, 702, 703, 704, 801, 802, 803, 804);

DELETE s FROM product_sku s
JOIN products p ON p.id = s.product_id
WHERE p.category_id IN (701, 702, 703, 704, 801, 802, 803, 804);

-- ------------------------------------------------------------
-- 3. 按商品名称重新生成
-- ------------------------------------------------------------

-- 3.1 手机壳（701）：颜色
INSERT INTO product_sku
  (product_id, sku_code, spec_json, spec_key, price, market_price, cost_price,
   stock, image, status, sort, create_time, update_time)
SELECT p.id, CONCAT(p.product_code, '-', v.sort),
       JSON_OBJECT('颜色', v.value), CONCAT('颜色:', v.value),
       p.sales_price, p.market_price, p.cost_price,
       GREATEST(1, FLOOR(p.stock / 3)) + CASE WHEN v.sort = 1 THEN MOD(p.stock, 3) ELSE 0 END,
       NULL, 1, v.sort - 1, NOW(), NOW()
FROM products p
JOIN product_spec_value v ON v.group_id = 1 AND v.sort <= 3
WHERE p.category_id = 701
  AND (p.name LIKE '%手机壳%' OR p.name LIKE '%保护壳%' OR p.name LIKE '%防摔壳%'
       OR p.name LIKE '%透明壳%' OR p.name LIKE '%磁吸壳%');

-- 3.2 鼠标（702）：颜色
INSERT INTO product_sku
  (product_id, sku_code, spec_json, spec_key, price, market_price, cost_price,
   stock, image, status, sort, create_time, update_time)
SELECT p.id, CONCAT(p.product_code, '-', v.sort),
       JSON_OBJECT('颜色', v.value), CONCAT('颜色:', v.value),
       p.sales_price, p.market_price, p.cost_price,
       GREATEST(1, FLOOR(p.stock / 3)) + CASE WHEN v.sort = 1 THEN MOD(p.stock, 3) ELSE 0 END,
       NULL, 1, v.sort - 1, NOW(), NOW()
FROM products p
JOIN product_spec_value v ON v.group_id = 1 AND v.sort <= 3
WHERE p.category_id = 702 AND p.name LIKE '%鼠标%';

-- 3.3 键盘（702）：颜色 × 轴体
INSERT INTO product_sku
  (product_id, sku_code, spec_json, spec_key, price, market_price, cost_price,
   stock, image, status, sort, create_time, update_time)
SELECT p.id, CONCAT(p.product_code, '-', v1.sort, v2.sort),
       JSON_OBJECT('颜色', v1.value, '轴体', v2.value),
       CONCAT('颜色:', v1.value, '|轴体:', v2.value),
       p.sales_price, p.market_price, p.cost_price,
       GREATEST(1, FLOOR(p.stock / 9)) + CASE WHEN v1.sort = 1 AND v2.sort = 1 THEN MOD(p.stock, 9) ELSE 0 END,
       NULL, 1, (v1.sort - 1) * 3 + (v2.sort - 1), NOW(), NOW()
FROM products p
JOIN product_spec_value v1 ON v1.group_id = 1 AND v1.sort <= 3
JOIN product_spec_value v2 ON v2.group_id = 6
WHERE p.category_id = 702 AND p.name LIKE '%键盘%';

-- 3.4 扩展坞 / USB Hub（702）：颜色
INSERT INTO product_sku
  (product_id, sku_code, spec_json, spec_key, price, market_price, cost_price,
   stock, image, status, sort, create_time, update_time)
SELECT p.id, CONCAT(p.product_code, '-', v.sort),
       JSON_OBJECT('颜色', v.value), CONCAT('颜色:', v.value),
       p.sales_price, p.market_price, p.cost_price,
       GREATEST(1, FLOOR(p.stock / 3)) + CASE WHEN v.sort = 1 THEN MOD(p.stock, 3) ELSE 0 END,
       NULL, 1, v.sort - 1, NOW(), NOW()
FROM products p
JOIN product_spec_value v ON v.group_id = 1 AND v.sort <= 3
WHERE p.category_id = 702
  AND (p.name LIKE '%扩展坞%' OR p.name LIKE '%Hub%' OR p.name LIKE '%hub%');

-- 3.5 硬盘 / SD卡 / TF卡 / U盘（703）：容量
INSERT INTO product_sku
  (product_id, sku_code, spec_json, spec_key, price, market_price, cost_price,
   stock, image, status, sort, create_time, update_time)
SELECT p.id, CONCAT(p.product_code, '-', v.sort),
       JSON_OBJECT('容量', v.value), CONCAT('容量:', v.value),
       p.sales_price
         + CASE v.value WHEN '512G' THEN 100 WHEN '1TB' THEN 500 WHEN '2TB' THEN 1500 ELSE 0 END,
       p.market_price
         + CASE v.value WHEN '512G' THEN 100 WHEN '1TB' THEN 500 WHEN '2TB' THEN 1500 ELSE 0 END,
       p.cost_price
         + CASE v.value WHEN '512G' THEN 100 WHEN '1TB' THEN 500 WHEN '2TB' THEN 1500 ELSE 0 END,
       GREATEST(1, FLOOR(p.stock / 4)) + CASE WHEN v.sort = 1 THEN MOD(p.stock, 4) ELSE 0 END,
       NULL, 1, v.sort - 1, NOW(), NOW()
FROM products p
JOIN product_spec_value v ON v.group_id = 2 AND v.value IN ('256G', '512G', '1TB', '2TB')
WHERE p.category_id = 703
  AND (p.name LIKE '%硬盘%' OR p.name LIKE '%SD卡%' OR p.name LIKE '%TF卡%' OR p.name LIKE '%U盘%');

-- 3.6 读卡器（703）：颜色
INSERT INTO product_sku
  (product_id, sku_code, spec_json, spec_key, price, market_price, cost_price,
   stock, image, status, sort, create_time, update_time)
SELECT p.id, CONCAT(p.product_code, '-', v.sort),
       JSON_OBJECT('颜色', v.value), CONCAT('颜色:', v.value),
       p.sales_price, p.market_price, p.cost_price,
       GREATEST(1, FLOOR(p.stock / 3)) + CASE WHEN v.sort = 1 THEN MOD(p.stock, 3) ELSE 0 END,
       NULL, 1, v.sort - 1, NOW(), NOW()
FROM products p
JOIN product_spec_value v ON v.group_id = 1 AND v.sort <= 3
WHERE p.category_id = 703 AND p.name LIKE '%读卡器%';

-- 3.7 充电宝 / 移动电源（704）：电池容量 × 颜色
INSERT INTO product_sku
  (product_id, sku_code, spec_json, spec_key, price, market_price, cost_price,
   stock, image, status, sort, create_time, update_time)
SELECT p.id, CONCAT(p.product_code, '-', v1.sort, v2.sort),
       JSON_OBJECT('电池容量', v1.value, '颜色', v2.value),
       CONCAT('电池容量:', v1.value, '|颜色:', v2.value),
       p.sales_price + CASE v1.value WHEN '20000mAh' THEN 100 ELSE 0 END,
       p.market_price + CASE v1.value WHEN '20000mAh' THEN 100 ELSE 0 END,
       p.cost_price + CASE v1.value WHEN '20000mAh' THEN 100 ELSE 0 END,
       GREATEST(1, FLOOR(p.stock / 6)) + CASE WHEN v1.sort = 1 AND v2.sort = 1 THEN MOD(p.stock, 6) ELSE 0 END,
       NULL, 1, (v1.sort - 1) * 3 + (v2.sort - 1), NOW(), NOW()
FROM products p
JOIN product_spec_value v1 ON v1.group_id = 10
JOIN product_spec_value v2 ON v2.group_id = 1 AND v2.sort <= 3
WHERE p.category_id = 704
  AND (p.name LIKE '%充电宝%' OR p.name LIKE '%移动电源%');

-- 3.8 充电器（704）：颜色
INSERT INTO product_sku
  (product_id, sku_code, spec_json, spec_key, price, market_price, cost_price,
   stock, image, status, sort, create_time, update_time)
SELECT p.id, CONCAT(p.product_code, '-', v.sort),
       JSON_OBJECT('颜色', v.value), CONCAT('颜色:', v.value),
       p.sales_price, p.market_price, p.cost_price,
       GREATEST(1, FLOOR(p.stock / 3)) + CASE WHEN v.sort = 1 THEN MOD(p.stock, 3) ELSE 0 END,
       NULL, 1, v.sort - 1, NOW(), NOW()
FROM products p
JOIN product_spec_value v ON v.group_id = 1 AND v.sort <= 3
WHERE p.category_id = 704 AND p.name LIKE '%充电器%';

-- 3.9 智能音箱（801）：颜色
INSERT INTO product_sku
  (product_id, sku_code, spec_json, spec_key, price, market_price, cost_price,
   stock, image, status, sort, create_time, update_time)
SELECT p.id, CONCAT(p.product_code, '-', v.sort),
       JSON_OBJECT('颜色', v.value), CONCAT('颜色:', v.value),
       p.sales_price, p.market_price, p.cost_price,
       GREATEST(1, FLOOR(p.stock / 3)) + CASE WHEN v.sort = 1 THEN MOD(p.stock, 3) ELSE 0 END,
       NULL, 1, v.sort - 1, NOW(), NOW()
FROM products p
JOIN product_spec_value v ON v.group_id = 1 AND v.sort <= 3
WHERE p.category_id = 801 AND p.name LIKE '%音箱%';

-- 3.10 摄像头（802）：颜色 × 清晰度
INSERT INTO product_sku
  (product_id, sku_code, spec_json, spec_key, price, market_price, cost_price,
   stock, image, status, sort, create_time, update_time)
SELECT p.id, CONCAT(p.product_code, '-', v1.sort, v2.sort),
       JSON_OBJECT('颜色', v1.value, '清晰度', v2.value),
       CONCAT('颜色:', v1.value, '|清晰度:', v2.value),
       p.sales_price + CASE v2.value WHEN '4K' THEN 100 ELSE 0 END,
       p.market_price + CASE v2.value WHEN '4K' THEN 100 ELSE 0 END,
       p.cost_price + CASE v2.value WHEN '4K' THEN 100 ELSE 0 END,
       GREATEST(1, FLOOR(p.stock / 6)) + CASE WHEN v1.sort = 1 AND v2.sort = 1 THEN MOD(p.stock, 6) ELSE 0 END,
       NULL, 1, (v1.sort - 1) * 2 + (v2.sort - 1), NOW(), NOW()
FROM products p
JOIN product_spec_value v1 ON v1.group_id = 1 AND v1.sort <= 3
JOIN product_spec_value v2 ON v2.group_id = 7
WHERE p.category_id = 802 AND p.name LIKE '%摄像头%';

-- 3.11 门磁 / 传感器（802）：颜色
INSERT INTO product_sku
  (product_id, sku_code, spec_json, spec_key, price, market_price, cost_price,
   stock, image, status, sort, create_time, update_time)
SELECT p.id, CONCAT(p.product_code, '-', v.sort),
       JSON_OBJECT('颜色', v.value), CONCAT('颜色:', v.value),
       p.sales_price, p.market_price, p.cost_price,
       GREATEST(1, FLOOR(p.stock / 3)) + CASE WHEN v.sort = 1 THEN MOD(p.stock, 3) ELSE 0 END,
       NULL, 1, v.sort - 1, NOW(), NOW()
FROM products p
JOIN product_spec_value v ON v.group_id = 1 AND v.sort <= 3
WHERE p.category_id = 802
  AND (p.name LIKE '%门磁%' OR p.name LIKE '%传感器%');

-- 3.12 灯泡 / 灯带 / 氛围灯（803）：色温
INSERT INTO product_sku
  (product_id, sku_code, spec_json, spec_key, price, market_price, cost_price,
   stock, image, status, sort, create_time, update_time)
SELECT p.id, CONCAT(p.product_code, '-', v.sort),
       JSON_OBJECT('色温', v.value), CONCAT('色温:', v.value),
       p.sales_price + CASE v.value WHEN '彩光' THEN 20 ELSE 0 END,
       p.market_price + CASE v.value WHEN '彩光' THEN 20 ELSE 0 END,
       p.cost_price + CASE v.value WHEN '彩光' THEN 20 ELSE 0 END,
       GREATEST(1, FLOOR(p.stock / 3)) + CASE WHEN v.sort = 1 THEN MOD(p.stock, 3) ELSE 0 END,
       NULL, 1, v.sort - 1, NOW(), NOW()
FROM products p
JOIN product_spec_value v ON v.group_id = 8
WHERE p.category_id = 803
  AND (p.name LIKE '%灯泡%' OR p.name LIKE '%灯带%' OR p.name LIKE '%氛围灯%');

-- 3.13 台灯 / 吸顶灯 / 开关（803）：颜色
INSERT INTO product_sku
  (product_id, sku_code, spec_json, spec_key, price, market_price, cost_price,
   stock, image, status, sort, create_time, update_time)
SELECT p.id, CONCAT(p.product_code, '-', v.sort),
       JSON_OBJECT('颜色', v.value), CONCAT('颜色:', v.value),
       p.sales_price, p.market_price, p.cost_price,
       GREATEST(1, FLOOR(p.stock / 3)) + CASE WHEN v.sort = 1 THEN MOD(p.stock, 3) ELSE 0 END,
       NULL, 1, v.sort - 1, NOW(), NOW()
FROM products p
JOIN product_spec_value v ON v.group_id = 1 AND v.sort <= 3
WHERE p.category_id = 803
  AND (p.name LIKE '%台灯%' OR p.name LIKE '%吸顶灯%' OR p.name LIKE '%开关%');

-- 3.14 扫地机器人（804）：颜色 × 版本
INSERT INTO product_sku
  (product_id, sku_code, spec_json, spec_key, price, market_price, cost_price,
   stock, image, status, sort, create_time, update_time)
SELECT p.id, CONCAT(p.product_code, '-', v1.sort, v2.sort),
       JSON_OBJECT('颜色', v1.value, '版本', v2.value),
       CONCAT('颜色:', v1.value, '|版本:', v2.value),
       p.sales_price + CASE v2.value WHEN '水箱版' THEN 500 ELSE 0 END,
       p.market_price + CASE v2.value WHEN '水箱版' THEN 500 ELSE 0 END,
       p.cost_price + CASE v2.value WHEN '水箱版' THEN 500 ELSE 0 END,
       GREATEST(1, FLOOR(p.stock / 6)) + CASE WHEN v1.sort = 1 AND v2.sort = 1 THEN MOD(p.stock, 6) ELSE 0 END,
       NULL, 1, (v1.sort - 1) * 2 + (v2.sort - 1), NOW(), NOW()
FROM products p
JOIN product_spec_value v1 ON v1.group_id = 1 AND v1.sort <= 3
JOIN product_spec_value v2 ON v2.group_id = 9
WHERE p.category_id = 804 AND (p.name LIKE '%扫地%' OR p.name LIKE '%扫拖%')
  AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id = p.id);

-- ------------------------------------------------------------
-- 4. 更新 SPU 规格定义
-- ------------------------------------------------------------
UPDATE products SET sku_type = 1,
  sku_specs = '[{"groupId":1,"name":"颜色","values":["曜石黑","雪域白","晴空蓝"]}]'
WHERE category_id = 701
  AND (name LIKE '%手机壳%' OR name LIKE '%保护壳%' OR name LIKE '%防摔壳%'
       OR name LIKE '%透明壳%' OR name LIKE '%磁吸壳%');

UPDATE products SET sku_type = 1,
  sku_specs = '[{"groupId":1,"name":"颜色","values":["曜石黑","雪域白","晴空蓝"]}]'
WHERE category_id = 702 AND name LIKE '%鼠标%';

UPDATE products SET sku_type = 1,
  sku_specs = '[{"groupId":1,"name":"颜色","values":["曜石黑","雪域白","晴空蓝"]},{"groupId":6,"name":"轴体","values":["红轴","青轴","茶轴"]}]'
WHERE category_id = 702 AND name LIKE '%键盘%';

UPDATE products SET sku_type = 1,
  sku_specs = '[{"groupId":1,"name":"颜色","values":["曜石黑","雪域白","晴空蓝"]}]'
WHERE category_id = 702
  AND (name LIKE '%扩展坞%' OR name LIKE '%Hub%' OR name LIKE '%hub%');

UPDATE products SET sku_type = 1,
  sku_specs = '[{"groupId":2,"name":"容量","values":["256G","512G","1TB","2TB"]}]'
WHERE category_id = 703
  AND (name LIKE '%硬盘%' OR name LIKE '%SD卡%' OR name LIKE '%TF卡%' OR name LIKE '%U盘%');

UPDATE products SET sku_type = 1,
  sku_specs = '[{"groupId":1,"name":"颜色","values":["曜石黑","雪域白","晴空蓝"]}]'
WHERE category_id = 703 AND name LIKE '%读卡器%';

UPDATE products SET sku_type = 1,
  sku_specs = '[{"groupId":10,"name":"电池容量","values":["10000mAh","20000mAh"]},{"groupId":1,"name":"颜色","values":["曜石黑","雪域白","晴空蓝"]}]'
WHERE category_id = 704
  AND (name LIKE '%充电宝%' OR name LIKE '%移动电源%');

UPDATE products SET sku_type = 1,
  sku_specs = '[{"groupId":1,"name":"颜色","values":["曜石黑","雪域白","晴空蓝"]}]'
WHERE category_id = 704 AND name LIKE '%充电器%';

UPDATE products SET sku_type = 1,
  sku_specs = '[{"groupId":1,"name":"颜色","values":["曜石黑","雪域白","晴空蓝"]}]'
WHERE category_id = 801 AND name LIKE '%音箱%';

UPDATE products SET sku_type = 1,
  sku_specs = '[{"groupId":1,"name":"颜色","values":["曜石黑","雪域白","晴空蓝"]},{"groupId":7,"name":"清晰度","values":["2K","4K"]}]'
WHERE category_id = 802 AND name LIKE '%摄像头%';

UPDATE products SET sku_type = 1,
  sku_specs = '[{"groupId":1,"name":"颜色","values":["曜石黑","雪域白","晴空蓝"]}]'
WHERE category_id = 802
  AND (name LIKE '%门磁%' OR name LIKE '%传感器%');

UPDATE products SET sku_type = 1,
  sku_specs = '[{"groupId":8,"name":"色温","values":["暖光","正白","彩光"]}]'
WHERE category_id = 803
  AND (name LIKE '%灯泡%' OR name LIKE '%灯带%' OR name LIKE '%氛围灯%');

UPDATE products SET sku_type = 1,
  sku_specs = '[{"groupId":1,"name":"颜色","values":["曜石黑","雪域白","晴空蓝"]}]'
WHERE category_id = 803
  AND (name LIKE '%台灯%' OR name LIKE '%吸顶灯%' OR name LIKE '%开关%');

UPDATE products SET sku_type = 1,
  sku_specs = '[{"groupId":1,"name":"颜色","values":["曜石黑","雪域白","晴空蓝"]},{"groupId":9,"name":"版本","values":["标准版","水箱版"]}]'
WHERE category_id = 804 AND (name LIKE '%扫地%' OR name LIKE '%扫拖%');

-- ------------------------------------------------------------
-- 5. 刷新 SPU 汇总
-- ------------------------------------------------------------
UPDATE products p SET
  p.stock = (SELECT COALESCE(SUM(s.stock), 0) FROM product_sku s
             WHERE s.product_id = p.id AND s.status = 1),
  p.sales_price = COALESCE((SELECT MIN(s.price) FROM product_sku s
                            WHERE s.product_id = p.id AND s.status = 1), p.sales_price),
  p.market_price = COALESCE((SELECT s.market_price FROM product_sku s
                             WHERE s.product_id = p.id AND s.status = 1
                             ORDER BY s.price ASC LIMIT 1), p.market_price),
  p.update_time = NOW()
WHERE p.sku_type = 1;

-- ------------------------------------------------------------
-- 6. 回填 cart / order_items
-- ------------------------------------------------------------
UPDATE cart c
JOIN (SELECT product_id, MIN(id) AS sku_id FROM product_sku GROUP BY product_id) x
  ON x.product_id = c.product_id
SET c.sku_id    = x.sku_id,
    c.sku_code  = (SELECT s.sku_code  FROM product_sku s WHERE s.id = x.sku_id),
    c.spec_json = (SELECT s.spec_key  FROM product_sku s WHERE s.id = x.sku_id)
WHERE c.sku_id IS NULL;

UPDATE order_items oi
JOIN (SELECT product_id, MIN(id) AS sku_id FROM product_sku GROUP BY product_id) x
  ON x.product_id = oi.product_id
SET oi.sku_id    = x.sku_id,
    oi.sku_code  = (SELECT s.sku_code  FROM product_sku s WHERE s.id = x.sku_id),
    oi.spec_json = (SELECT s.spec_key  FROM product_sku s WHERE s.id = x.sku_id)
WHERE oi.sku_id IS NULL;

-- ------------------------------------------------------------
-- 7. 校验
-- --------------------------------------------------------------
SELECT COUNT(*) AS 缺SKU商品数
FROM products p
WHERE NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id = p.id);

SELECT p.id, p.name, p.sku_specs, COUNT(s.id) AS sku数
FROM products p LEFT JOIN product_sku s ON s.product_id = p.id
WHERE p.category_id IN (701, 702, 703, 704, 801, 802, 803, 804)
GROUP BY p.id, p.name, p.sku_specs
ORDER BY p.category_id, p.id;
