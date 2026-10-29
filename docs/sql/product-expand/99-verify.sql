-- =====================================================================
-- 商品扩容 99：数据校验
-- =====================================================================
SET NAMES utf8mb4;

SELECT COUNT(*) AS top_count FROM categories WHERE parent_id IS NULL;            -- 期望 8
SELECT COUNT(*) AS child_count FROM categories WHERE parent_id IS NOT NULL;      -- 期望 21
SELECT COUNT(*) AS product_count FROM products;                                  -- 期望 300

-- 每个叶子 14~15
SELECT c.id, c.name, COUNT(p.id) AS cnt
FROM categories c
LEFT JOIN products p ON p.category_id = c.id AND p.listing_status = 1
WHERE c.parent_id IS NOT NULL
GROUP BY c.id, c.name
ORDER BY c.id;

-- 一级汇总：30/43/29/43/29/14/56/56
SELECT COALESCE(c.parent_id, c.id) AS top_id, COUNT(*) AS cnt
FROM products p JOIN categories c ON p.category_id = c.id
GROUP BY top_id
ORDER BY top_id;

-- 种子商品与 specs
SELECT COUNT(*) AS seed_count FROM products WHERE id BETWEEN 51 AND 300 AND product_code LIKE 'SEED-%';
SELECT COUNT(*) AS specs_null FROM products WHERE specs IS NULL;
SELECT COUNT(*) AS legacy_specs FROM products WHERE id <= 50 AND specs IS NOT NULL;
SELECT id, name, category_id, specs FROM products ORDER BY id LIMIT 3;
