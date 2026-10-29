-- =====================================================================
-- 商品扩容 99：数据校验
-- =====================================================================
SET NAMES utf8mb4;

SELECT COUNT(*) AS top_count FROM categories WHERE parent_id IS NULL;            -- 期望 8（生产可能有额外空分类）
SELECT COUNT(*) AS child_count FROM categories WHERE parent_id IS NOT NULL;      -- 期望 21（502 并入 501，新增 503 机械普通手表）
SELECT COUNT(*) AS product_count FROM products;                                  -- 期望 136（126 + 10 机械表）

-- 每个叶子目标 6 个（501 智能手表 12 个、503 机械普通手表 10 个）
SELECT c.id, c.name, COUNT(p.id) AS cnt
FROM categories c
LEFT JOIN products p ON p.category_id = c.id AND p.listing_status = 1
WHERE c.parent_id IS NOT NULL
GROUP BY c.id, c.name
ORDER BY c.id;

-- 一级汇总
SELECT COALESCE(c.parent_id, c.id) AS top_id, COUNT(*) AS cnt
FROM products p JOIN categories c ON p.category_id = c.id
GROUP BY top_id
ORDER BY top_id;

-- 种子商品、specs、名称唯一性
SELECT COUNT(*) AS seed_count FROM products WHERE product_code LIKE 'SEED-%';     -- 期望 86（51~136）
SELECT COUNT(*) AS specs_null FROM products WHERE specs IS NULL;                  -- 期望 0
SELECT COUNT(*) AS legacy_specs FROM products WHERE id <= 50 AND specs IS NOT NULL; -- 期望 50
SELECT COUNT(*) AS total_names, COUNT(DISTINCT name) AS distinct_names FROM products;
SELECT id, name, category_id, specs FROM products ORDER BY id LIMIT 3;

-- title 重命名（06a 之后）
SELECT COUNT(*) AS title_null FROM products WHERE title IS NULL;                  -- 期望 0
SELECT MIN(CHAR_LENGTH(title)) AS title_min_len, MAX(CHAR_LENGTH(title)) AS title_max_len FROM products;
-- 06b 执行后期望 0
SELECT COUNT(*) AS description_column
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND COLUMN_NAME = 'description';
