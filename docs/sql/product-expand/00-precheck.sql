-- =====================================================================
-- 商品扩容 00：只读预检（不修改数据）
-- 执行前先备份数据库。
-- =====================================================================
SET NAMES utf8mb4;

SELECT VERSION() AS mysql_version, DATABASE() AS db_name;

-- 1. 现有数量
SELECT 'products' AS t, COUNT(*) AS cnt FROM products
UNION ALL SELECT 'categories', COUNT(*) FROM categories
UNION ALL SELECT 'products_specs_null', COUNT(*) FROM products WHERE 1 = 0;

-- 2. 分类现状
SELECT id, name, parent_id, sort_order, recommend FROM categories ORDER BY COALESCE(parent_id, id), parent_id IS NOT NULL, sort_order;

-- 3. id 冲突检查（都应为 0）
SELECT 'category_7_8' AS k, COUNT(*) AS c FROM categories WHERE id IN (7, 8)
UNION ALL SELECT 'category_new_children', COUNT(*) FROM categories WHERE id IN (701,702,703,704,801,802,803,804)
UNION ALL SELECT 'product_51_300', COUNT(*) FROM products WHERE id BETWEEN 51 AND 300;

-- 4. 字段检查：specs 是否已存在、shop_id 是否仍在
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products'
  AND COLUMN_NAME IN ('specs', 'shop_id');

-- 5. 每叶子商品数（扩容前）
SELECT c.id, c.name, COUNT(p.id) AS cnt
FROM categories c
LEFT JOIN products p ON p.category_id = c.id AND p.listing_status = 1
WHERE c.parent_id IS NOT NULL
GROUP BY c.id, c.name
ORDER BY c.id;
