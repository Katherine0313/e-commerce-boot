-- =====================================================================
-- 商品扩容 02：分类重构（8 个一级 + 21 个二级）
-- 可重复执行；不删除旧分类，不重建表
-- =====================================================================
SET NAMES utf8mb4;

-- 1. 一级分类：现有 1-6 只调整名称/排序，新增 7、8
UPDATE categories SET name = '手机',   sort_order = 1 WHERE id = 1;
UPDATE categories SET name = '电脑',   sort_order = 2 WHERE id = 2;
UPDATE categories SET name = '平板',   sort_order = 3 WHERE id = 3;
UPDATE categories SET name = '耳机音箱', sort_order = 4 WHERE id = 4;
UPDATE categories SET name = '手表',   sort_order = 5 WHERE id = 5;
UPDATE categories SET name = '相机',   sort_order = 6 WHERE id = 6;

INSERT IGNORE INTO categories (id, name, parent_id, sort_order, status, recommend, image)
VALUES
(7, '数码配件', NULL, 7, 1, 0, NULL),
(8, '智能家居', NULL, 8, 1, 0, NULL);

-- 2. 现有二级：统一排序（401/402/403 保留在耳机音箱下）
UPDATE categories SET sort_order = 1 WHERE id = 101;
UPDATE categories SET sort_order = 2 WHERE id = 102;
UPDATE categories SET sort_order = 1 WHERE id = 201;
UPDATE categories SET sort_order = 2 WHERE id = 202;
UPDATE categories SET sort_order = 3 WHERE id = 203;
UPDATE categories SET sort_order = 1 WHERE id = 301;
UPDATE categories SET sort_order = 2 WHERE id = 302;
UPDATE categories SET sort_order = 1 WHERE id = 401;
UPDATE categories SET sort_order = 2 WHERE id = 402;
UPDATE categories SET sort_order = 3 WHERE id = 403;
UPDATE categories SET sort_order = 1 WHERE id = 501;
UPDATE categories SET sort_order = 2 WHERE id = 502;
UPDATE categories SET sort_order = 1 WHERE id = 601;

-- 首页热门推荐使用的重点二级分类（与本地一致）
UPDATE categories SET recommend = 1 WHERE id IN (101, 102, 201, 301, 501, 601);

-- 3. 新增二级：先父后子（INSERT IGNORE 保证重复执行不报错）
INSERT IGNORE INTO categories (id, name, parent_id, sort_order, status, recommend, image)
VALUES
(701, '手机配件', 7, 1, 1, 0, NULL),
(702, '电脑配件', 7, 2, 1, 0, NULL),
(703, '存储设备', 7, 3, 1, 0, NULL),
(704, '充电设备', 7, 4, 1, 0, NULL),
(801, '智能音箱', 8, 1, 1, 0, NULL),
(802, '智能安防', 8, 2, 1, 0, NULL),
(803, '智能照明', 8, 3, 1, 0, NULL),
(804, '扫地机器人', 8, 4, 1, 0, NULL);

-- 4. 验证：一级 8、二级 21
SELECT COUNT(*) AS top_count FROM categories WHERE parent_id IS NULL;
SELECT COUNT(*) AS child_count FROM categories WHERE parent_id IS NOT NULL;
SELECT id, name, parent_id, sort_order FROM categories ORDER BY COALESCE(parent_id, id), parent_id IS NOT NULL, sort_order;
