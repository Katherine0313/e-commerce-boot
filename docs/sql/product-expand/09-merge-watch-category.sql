-- =====================================================================
-- 09：合并「运动手表」(502) 到「智能手表」(501)
-- 原因：两个二级分类边界模糊，种子生成时按叶子轮询分配，导致
--       Watch Ultra / Ultra 2、Watch GT 4 / GT 5 等同系列被拆到两类。
-- 可重复执行
-- =====================================================================
SET NAMES utf8mb4;

-- 1. 商品迁到 501（502 下无子分类，可直接删）
UPDATE products SET category_id = 501 WHERE category_id = 502;

-- 2. 删除 502
DELETE FROM categories WHERE id = 502;

-- 3. 验证：手表一级下应只剩 501，商品 12 件；二级总数 20
SELECT c.id, c.name, COUNT(p.id) AS cnt
FROM categories c LEFT JOIN products p ON p.category_id = c.id
WHERE c.parent_id = 5
GROUP BY c.id, c.name;

SELECT COUNT(*) AS top_count FROM categories WHERE parent_id IS NULL;
SELECT COUNT(*) AS child_count FROM categories WHERE parent_id IS NOT NULL;
