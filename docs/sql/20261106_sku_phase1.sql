-- ============================================================
-- SKU 多规格改造 · P0（建表 + 规格字典 + 批量生成 SKU）
-- 2026-11-06
-- 兼容 MySQL 5.7 / 8.0
-- 执行：mysql --default-character-set=utf8mb4 -u <user> -p <db> < 本文件
-- 前置：执行前必须先备份数据库
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 规格字典表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS product_spec_group (
  id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  category_id BIGINT      NULL COMMENT '所属分类；NULL 表示全局通用',
  name        VARCHAR(32) NOT NULL COMMENT '规格名，如 颜色 / 存储容量 / 运行内存',
  sort        INT         NOT NULL DEFAULT 0 COMMENT '排序',
  status      TINYINT     NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
  create_time DATETIME    NOT NULL,
  update_time DATETIME    NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_category_name (category_id, name),
  KEY idx_group_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='销售规格名（字典）';

CREATE TABLE IF NOT EXISTS product_spec_value (
  id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  group_id    BIGINT      NOT NULL COMMENT '所属规格名 product_spec_group.id',
  value       VARCHAR(64) NOT NULL COMMENT '规格值，如 曜石黑 / 256G',
  sort        INT         NOT NULL DEFAULT 0 COMMENT '排序',
  create_time DATETIME    NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_value_group_value (group_id, value),
  KEY idx_value_group (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='销售规格值（字典）';

-- ------------------------------------------------------------
-- 2. 商品 SKU 表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS product_sku (
  id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  product_id    BIGINT        NOT NULL COMMENT '所属 SPU（products.id）',
  sku_code      VARCHAR(64)   NULL COMMENT '商家编码',
  spec_json     JSON          NOT NULL COMMENT '规格组合 {"颜色":"曜石黑","存储容量":"256G"}',
  spec_key      VARCHAR(255)  NOT NULL COMMENT '规格规范化串，用于唯一约束，如 颜色:曜石黑|存储容量:256G',
  price         DECIMAL(10,2) NOT NULL COMMENT '销售价',
  market_price  DECIMAL(10,2) NULL COMMENT '划线价',
  cost_price    DECIMAL(10,2) NULL COMMENT '成本价（后台字段）',
  stock         INT           NOT NULL DEFAULT 0 COMMENT '可售库存',
  image         VARCHAR(255)  NULL COMMENT 'SKU 图；为空则用 SPU 主图',
  status        TINYINT       NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
  sort          INT           NOT NULL DEFAULT 0 COMMENT '展示顺序',
  create_time   DATETIME      NOT NULL,
  update_time   DATETIME      NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sku_spec (product_id, spec_key),
  KEY idx_sku_product (product_id),
  KEY idx_sku_status_stock (status, stock)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品 SKU（多规格）';

-- ------------------------------------------------------------
-- 3. products 增量（SPU 标记 + 规格定义）
-- ------------------------------------------------------------
ALTER TABLE products
  ADD COLUMN sku_type  TINYINT NOT NULL DEFAULT 0
      COMMENT '0 单规格 / 1 多规格' AFTER specs,
  ADD COLUMN sku_specs TEXT NULL
      COMMENT '销售规格维度定义 JSON（引用规格字典）' AFTER sku_type;

-- ------------------------------------------------------------
-- 4. cart 增量（按 SKU 加购）
-- ------------------------------------------------------------
ALTER TABLE cart
  ADD COLUMN sku_id    BIGINT       NULL COMMENT 'SKU ID' AFTER product_id,
  ADD COLUMN spec_json VARCHAR(255) NULL COMMENT '规格快照（展示用）',
  ADD COLUMN sku_code  VARCHAR(64)  NULL COMMENT '商家编码快照';

-- 注意：旧唯一索引 uk_cart_user_product(user_id, product_id) 同时承担两个外键的索引依赖：
--   fk_cart_product -> product_id
--   fk_cart_user    -> user_id
-- 必须先把 product_id、user_id 各自的普通索引补齐，才能删旧唯一索引
ALTER TABLE cart ADD KEY idx_cart_product (product_id);
ALTER TABLE cart ADD KEY idx_cart_user (user_id);
ALTER TABLE cart DROP INDEX uk_cart_user_product;
ALTER TABLE cart ADD UNIQUE KEY uk_cart_user_sku (user_id, sku_id);

-- ------------------------------------------------------------
-- 5. order_items 增量（下单规格快照）
-- ------------------------------------------------------------
ALTER TABLE order_items
  ADD COLUMN sku_id    BIGINT       NULL COMMENT '下单时 SKU ID 快照' AFTER product_id,
  ADD COLUMN sku_code  VARCHAR(64)  NULL COMMENT '商家编码快照',
  ADD COLUMN spec_json VARCHAR(255) NULL COMMENT '下单时规格快照，如 颜色:曜石黑|存储容量:256G';

-- ------------------------------------------------------------
-- 6. 规格字典种子数据
--    固定 group id，便于 products.sku_specs 引用
-- ------------------------------------------------------------
INSERT INTO product_spec_group (id, category_id, name, sort, status, create_time, update_time) VALUES
  (1, NULL, '颜色',     1, 1, NOW(), NOW()),
  (2, NULL, '存储容量', 2, 1, NOW(), NOW()),
  (3, NULL, '运行内存', 3, 1, NOW(), NOW()),
  (4, NULL, '表带材质', 4, 1, NOW(), NOW()),
  (5, NULL, '套装类型', 5, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO product_spec_value (group_id, value, sort, create_time) VALUES
  (1, '曜石黑', 1, NOW()),
  (1, '雪域白', 2, NOW()),
  (1, '晴空蓝', 3, NOW()),
  (2, '128G', 1, NOW()),
  (2, '256G', 2, NOW()),
  (2, '512G', 3, NOW()),
  (3, '16G', 1, NOW()),
  (3, '32G', 2, NOW()),
  (4, '运动表带', 1, NOW()),
  (4, '皮革表带', 2, NOW()),
  (4, '金属表链', 3, NOW()),
  (5, '单机身', 1, NOW()),
  (5, '标准套机', 2, NOW())
ON DUPLICATE KEY UPDATE sort = VALUES(sort);

-- ------------------------------------------------------------
-- 7. 按分类批量生成 SKU
--    规则：
--      手机    颜色(3) × 存储容量(3)   256G+500  512G+1500  蓝+100
--      电脑    颜色(3) × 运行内存(2)   32G +1500  蓝+100
--      平板    颜色(3) × 存储容量(3)   256G+400  512G+1200  蓝+100
--      耳机音箱 颜色(3)
--      手表    颜色(3) × 表带材质(3)   皮革+200  金属+500   蓝+100
--      相机    颜色(2) × 套装类型(2)   套机+1500
--      配件    颜色(3)
--      智能家居 颜色(2)
--    库存：原 stock 平均分配到各 SKU，余数补给第一个组合
-- ------------------------------------------------------------

-- 7.1 手机（101 苹果手机 / 102 安卓手机）
INSERT INTO product_sku
  (product_id, sku_code, spec_json, spec_key, price, market_price, cost_price,
   stock, image, status, sort, create_time, update_time)
SELECT p.id,
       CONCAT(p.product_code, '-', v1.sort, v2.sort),
       JSON_OBJECT('颜色', v1.value, '存储容量', v2.value),
       CONCAT('颜色:', v1.value, '|存储容量:', v2.value),
       p.sales_price
         + CASE WHEN v1.value = '晴空蓝' THEN 100 ELSE 0 END
         + CASE v2.value WHEN '256G' THEN 500 WHEN '512G' THEN 1500 ELSE 0 END,
       p.market_price
         + CASE WHEN v1.value = '晴空蓝' THEN 100 ELSE 0 END
         + CASE v2.value WHEN '256G' THEN 500 WHEN '512G' THEN 1500 ELSE 0 END,
       p.cost_price
         + CASE WHEN v1.value = '晴空蓝' THEN 100 ELSE 0 END
         + CASE v2.value WHEN '256G' THEN 500 WHEN '512G' THEN 1500 ELSE 0 END,
       GREATEST(1, FLOOR(p.stock / 6))
         + CASE WHEN v1.sort = 1 AND v2.sort = 1 THEN MOD(p.stock, 6) ELSE 0 END,
       NULL, 1, (v1.sort - 1) * 3 + (v2.sort - 1), NOW(), NOW()
FROM products p
JOIN product_spec_value v1 ON v1.group_id = 1 AND v1.sort <= 3
JOIN product_spec_value v2 ON v2.group_id = 2
WHERE p.category_id IN (101, 102)
  AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id = p.id);

-- 7.2 电脑（201 游戏本 / 202 商务本 / 203 轻薄本）
INSERT INTO product_sku
  (product_id, sku_code, spec_json, spec_key, price, market_price, cost_price,
   stock, image, status, sort, create_time, update_time)
SELECT p.id,
       CONCAT(p.product_code, '-', v1.sort, v2.sort),
       JSON_OBJECT('颜色', v1.value, '运行内存', v2.value),
       CONCAT('颜色:', v1.value, '|运行内存:', v2.value),
       p.sales_price
         + CASE WHEN v1.value = '晴空蓝' THEN 100 ELSE 0 END
         + CASE v2.value WHEN '32G' THEN 1500 ELSE 0 END,
       p.market_price
         + CASE WHEN v1.value = '晴空蓝' THEN 100 ELSE 0 END
         + CASE v2.value WHEN '32G' THEN 1500 ELSE 0 END,
       p.cost_price
         + CASE WHEN v1.value = '晴空蓝' THEN 100 ELSE 0 END
         + CASE v2.value WHEN '32G' THEN 1500 ELSE 0 END,
       GREATEST(1, FLOOR(p.stock / 6))
         + CASE WHEN v1.sort = 1 AND v2.sort = 1 THEN MOD(p.stock, 6) ELSE 0 END,
       NULL, 1, (v1.sort - 1) * 2 + (v2.sort - 1), NOW(), NOW()
FROM products p
JOIN product_spec_value v1 ON v1.group_id = 1 AND v1.sort <= 3
JOIN product_spec_value v2 ON v2.group_id = 3
WHERE p.category_id IN (201, 202, 203)
  AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id = p.id);

-- 7.3 平板（301 iPad / 302 安卓平板）
INSERT INTO product_sku
  (product_id, sku_code, spec_json, spec_key, price, market_price, cost_price,
   stock, image, status, sort, create_time, update_time)
SELECT p.id,
       CONCAT(p.product_code, '-', v1.sort, v2.sort),
       JSON_OBJECT('颜色', v1.value, '存储容量', v2.value),
       CONCAT('颜色:', v1.value, '|存储容量:', v2.value),
       p.sales_price
         + CASE WHEN v1.value = '晴空蓝' THEN 100 ELSE 0 END
         + CASE v2.value WHEN '256G' THEN 400 WHEN '512G' THEN 1200 ELSE 0 END,
       p.market_price
         + CASE WHEN v1.value = '晴空蓝' THEN 100 ELSE 0 END
         + CASE v2.value WHEN '256G' THEN 400 WHEN '512G' THEN 1200 ELSE 0 END,
       p.cost_price
         + CASE WHEN v1.value = '晴空蓝' THEN 100 ELSE 0 END
         + CASE v2.value WHEN '256G' THEN 400 WHEN '512G' THEN 1200 ELSE 0 END,
       GREATEST(1, FLOOR(p.stock / 6))
         + CASE WHEN v1.sort = 1 AND v2.sort = 1 THEN MOD(p.stock, 6) ELSE 0 END,
       NULL, 1, (v1.sort - 1) * 3 + (v2.sort - 1), NOW(), NOW()
FROM products p
JOIN product_spec_value v1 ON v1.group_id = 1 AND v1.sort <= 3
JOIN product_spec_value v2 ON v2.group_id = 2
WHERE p.category_id IN (301, 302)
  AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id = p.id);

-- 7.4 耳机音箱（401 头戴式耳机 / 402 无线耳机 / 403 蓝牙音箱）
INSERT INTO product_sku
  (product_id, sku_code, spec_json, spec_key, price, market_price, cost_price,
   stock, image, status, sort, create_time, update_time)
SELECT p.id,
       CONCAT(p.product_code, '-', v1.sort),
       JSON_OBJECT('颜色', v1.value),
       CONCAT('颜色:', v1.value),
       p.sales_price + CASE WHEN v1.value = '晴空蓝' THEN 100 ELSE 0 END,
       p.market_price + CASE WHEN v1.value = '晴空蓝' THEN 100 ELSE 0 END,
       p.cost_price + CASE WHEN v1.value = '晴空蓝' THEN 100 ELSE 0 END,
       GREATEST(1, FLOOR(p.stock / 3))
         + CASE WHEN v1.sort = 1 THEN MOD(p.stock, 3) ELSE 0 END,
       NULL, 1, v1.sort - 1, NOW(), NOW()
FROM products p
JOIN product_spec_value v1 ON v1.group_id = 1 AND v1.sort <= 3
WHERE p.category_id IN (401, 402, 403)
  AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id = p.id);

-- 7.5 手表（501 智能手表 / 503 机械普通手表）
INSERT INTO product_sku
  (product_id, sku_code, spec_json, spec_key, price, market_price, cost_price,
   stock, image, status, sort, create_time, update_time)
SELECT p.id,
       CONCAT(p.product_code, '-', v1.sort, v2.sort),
       JSON_OBJECT('颜色', v1.value, '表带材质', v2.value),
       CONCAT('颜色:', v1.value, '|表带材质:', v2.value),
       p.sales_price
         + CASE WHEN v1.value = '晴空蓝' THEN 100 ELSE 0 END
         + CASE v2.value WHEN '皮革表带' THEN 200 WHEN '金属表链' THEN 500 ELSE 0 END,
       p.market_price
         + CASE WHEN v1.value = '晴空蓝' THEN 100 ELSE 0 END
         + CASE v2.value WHEN '皮革表带' THEN 200 WHEN '金属表链' THEN 500 ELSE 0 END,
       p.cost_price
         + CASE WHEN v1.value = '晴空蓝' THEN 100 ELSE 0 END
         + CASE v2.value WHEN '皮革表带' THEN 200 WHEN '金属表链' THEN 500 ELSE 0 END,
       GREATEST(1, FLOOR(p.stock / 9))
         + CASE WHEN v1.sort = 1 AND v2.sort = 1 THEN MOD(p.stock, 9) ELSE 0 END,
       NULL, 1, (v1.sort - 1) * 3 + (v2.sort - 1), NOW(), NOW()
FROM products p
JOIN product_spec_value v1 ON v1.group_id = 1 AND v1.sort <= 3
JOIN product_spec_value v2 ON v2.group_id = 4
WHERE p.category_id IN (501, 503)
  AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id = p.id);

-- 7.6 相机（601 微单相机）
INSERT INTO product_sku
  (product_id, sku_code, spec_json, spec_key, price, market_price, cost_price,
   stock, image, status, sort, create_time, update_time)
SELECT p.id,
       CONCAT(p.product_code, '-', v1.sort, v2.sort),
       JSON_OBJECT('颜色', v1.value, '套装类型', v2.value),
       CONCAT('颜色:', v1.value, '|套装类型:', v2.value),
       p.sales_price + CASE v2.value WHEN '标准套机' THEN 1500 ELSE 0 END,
       p.market_price + CASE v2.value WHEN '标准套机' THEN 1500 ELSE 0 END,
       p.cost_price + CASE v2.value WHEN '标准套机' THEN 1500 ELSE 0 END,
       GREATEST(1, FLOOR(p.stock / 4))
         + CASE WHEN v1.sort = 1 AND v2.sort = 1 THEN MOD(p.stock, 4) ELSE 0 END,
       NULL, 1, (v1.sort - 1) * 2 + (v2.sort - 1), NOW(), NOW()
FROM products p
JOIN product_spec_value v1 ON v1.group_id = 1 AND v1.sort <= 2
JOIN product_spec_value v2 ON v2.group_id = 5
WHERE p.category_id IN (601)
  AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id = p.id);

-- 7.7 数码配件（701 手机配件 / 702 电脑配件 / 703 存储设备 / 704 充电设备）
INSERT INTO product_sku
  (product_id, sku_code, spec_json, spec_key, price, market_price, cost_price,
   stock, image, status, sort, create_time, update_time)
SELECT p.id,
       CONCAT(p.product_code, '-', v1.sort),
       JSON_OBJECT('颜色', v1.value),
       CONCAT('颜色:', v1.value),
       p.sales_price + CASE WHEN v1.value = '晴空蓝' THEN 100 ELSE 0 END,
       p.market_price + CASE WHEN v1.value = '晴空蓝' THEN 100 ELSE 0 END,
       p.cost_price + CASE WHEN v1.value = '晴空蓝' THEN 100 ELSE 0 END,
       GREATEST(1, FLOOR(p.stock / 3))
         + CASE WHEN v1.sort = 1 THEN MOD(p.stock, 3) ELSE 0 END,
       NULL, 1, v1.sort - 1, NOW(), NOW()
FROM products p
JOIN product_spec_value v1 ON v1.group_id = 1 AND v1.sort <= 3
WHERE p.category_id IN (701, 702, 703, 704)
  AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id = p.id);

-- 7.8 智能家居（801 智能音箱 / 802 智能安防 / 803 智能照明 / 804 扫地机器人）
INSERT INTO product_sku
  (product_id, sku_code, spec_json, spec_key, price, market_price, cost_price,
   stock, image, status, sort, create_time, update_time)
SELECT p.id,
       CONCAT(p.product_code, '-', v1.sort),
       JSON_OBJECT('颜色', v1.value),
       CONCAT('颜色:', v1.value),
       p.sales_price,
       p.market_price,
       p.cost_price,
       GREATEST(1, FLOOR(p.stock / 2))
         + CASE WHEN v1.sort = 1 THEN MOD(p.stock, 2) ELSE 0 END,
       NULL, 1, v1.sort - 1, NOW(), NOW()
FROM products p
JOIN product_spec_value v1 ON v1.group_id = 1 AND v1.sort <= 2
WHERE p.category_id IN (801, 802, 803, 804)
  AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id = p.id);

-- ------------------------------------------------------------
-- 8. 更新 SPU 规格定义
-- ------------------------------------------------------------
UPDATE products SET sku_type = 1,
  sku_specs = '[{"groupId":1,"name":"颜色","values":["曜石黑","雪域白","晴空蓝"]},{"groupId":2,"name":"存储容量","values":["128G","256G","512G"]}]'
WHERE category_id IN (101, 102);

UPDATE products SET sku_type = 1,
  sku_specs = '[{"groupId":1,"name":"颜色","values":["曜石黑","雪域白","晴空蓝"]},{"groupId":3,"name":"运行内存","values":["16G","32G"]}]'
WHERE category_id IN (201, 202, 203);

UPDATE products SET sku_type = 1,
  sku_specs = '[{"groupId":1,"name":"颜色","values":["曜石黑","雪域白","晴空蓝"]},{"groupId":2,"name":"存储容量","values":["128G","256G","512G"]}]'
WHERE category_id IN (301, 302);

UPDATE products SET sku_type = 1,
  sku_specs = '[{"groupId":1,"name":"颜色","values":["曜石黑","雪域白","晴空蓝"]}]'
WHERE category_id IN (401, 402, 403, 701, 702, 703, 704);

UPDATE products SET sku_type = 1,
  sku_specs = '[{"groupId":1,"name":"颜色","values":["曜石黑","雪域白","晴空蓝"]},{"groupId":4,"name":"表带材质","values":["运动表带","皮革表带","金属表链"]}]'
WHERE category_id IN (501, 503);

UPDATE products SET sku_type = 1,
  sku_specs = '[{"groupId":1,"name":"颜色","values":["曜石黑","雪域白"]},{"groupId":5,"name":"套装类型","values":["单机身","标准套机"]}]'
WHERE category_id IN (601);

UPDATE products SET sku_type = 1,
  sku_specs = '[{"groupId":1,"name":"颜色","values":["曜石黑","雪域白"]}]'
WHERE category_id IN (801, 802, 803, 804);

-- ------------------------------------------------------------
-- 9. 刷新 SPU 汇总字段（最低价 / 对应划线价 / 库存合计）
-- ------------------------------------------------------------
UPDATE products p SET
  p.stock = (SELECT COALESCE(SUM(s.stock), 0) FROM product_sku s
             WHERE s.product_id = p.id AND s.status = 1),
  p.sales_price = (SELECT MIN(s.price) FROM product_sku s
                   WHERE s.product_id = p.id AND s.status = 1),
  p.market_price = (SELECT s.market_price FROM product_sku s
                    WHERE s.product_id = p.id AND s.status = 1
                    ORDER BY s.price ASC LIMIT 1),
  p.update_time = NOW()
WHERE p.sku_type = 1;

-- ------------------------------------------------------------
-- 10. 回填存量 cart / order_items（指向该商品第一个 SKU）
--     仅用于历史数据兼容，新数据由业务写入
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
-- 11. 校验
-- ------------------------------------------------------------
SELECT COUNT(*) AS 商品总数,
       SUM(sku_type = 1) AS 多规格商品数,
       SUM(sku_type = 0) AS 单规格商品数
FROM products;

SELECT COUNT(*) AS SKU总数,
       COUNT(DISTINCT product_id) AS 覆盖商品数,
       MIN(price) AS 最低价,
       MAX(price) AS 最高价
FROM product_sku;

-- 应返回 0：存在商品但没有 SKU
SELECT COUNT(*) AS 缺SKU商品数
FROM products p
WHERE NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id = p.id);

-- 抽样查看
SELECT p.name, s.sku_code, s.spec_key, s.price, s.stock
FROM products p JOIN product_sku s ON s.product_id = p.id
WHERE p.category_id = 101
ORDER BY p.id, s.sort
LIMIT 12;
