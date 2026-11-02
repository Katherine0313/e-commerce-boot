-- ============================================================================
-- 后台商品评价管理：菜单与角色授权（2026-11-02）
--
-- 挂在【订单管理】下面（parent_id = 3）：评价由订单产生，跟着订单管理走最顺。
-- 后台前端路由是静态表（e-commerce/src/router/index.js），菜单走 permission 过滤；
-- 这里补 admin_menu / admin_role_menu 是为了让「角色管理」「菜单管理」页能正确显示与授权。
--
-- 菜单 id 分配（避开已被占用的 id）：
--   202 商品评价（菜单，perms = product:review:list）
--   230 评价回复 / 231 评价新增 / 232 评价编辑 / 233 评价删除 / 234 评价导出（按钮）
--
-- 角色：
--   2 运营   → 菜单 + 全部按钮（回复/新增虚拟/编辑虚拟/删除/导出）
--   3 客服   → 菜单 + 回复（客服日常要回复评价）
--   1 店铺管理员 → 不需要配，系统按 *:*:* 放行全部权限
--
-- 幂等：可重复执行（INSERT IGNORE，主键冲突自动跳过）。
--
-- 执行前请先备份数据库。
--   本地：mysql -u<user> -p<pass> --default-character-set=utf8mb4 e_commerce_db < 02-admin-review-menu.sql
--   生产：先 mysqldump 备份，再执行同一份（经 stdin 管道导入，服务器不落文件）
-- ============================================================================

-- ------------------------------------------------------------
-- 1. 菜单：商品评价（挂在订单管理下）
-- ------------------------------------------------------------
INSERT IGNORE INTO `admin_menu`
    (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `sort_order`, `visible`, `status`)
VALUES
    (202, 3, '商品评价', 'C', '/orders/review', NULL, 'product:review:list', NULL, 3, 1, 1);

-- ------------------------------------------------------------
-- 2. 按钮权限（挂在商品评价下）
-- ------------------------------------------------------------
INSERT IGNORE INTO `admin_menu`
    (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `sort_order`, `visible`, `status`)
VALUES
    (230, 202, '评价回复', 'F', NULL, NULL, 'product:review:reply',  NULL, 1, 1, 1),
    (231, 202, '评价新增', 'F', NULL, NULL, 'product:review:add',    NULL, 2, 1, 1),
    (232, 202, '评价编辑', 'F', NULL, NULL, 'product:review:edit',   NULL, 3, 1, 1),
    (233, 202, '评价删除', 'F', NULL, NULL, 'product:review:remove', NULL, 4, 1, 1),
    (234, 202, '评价导出', 'F', NULL, NULL, 'product:review:export', NULL, 5, 1, 1);

-- ------------------------------------------------------------
-- 3. 角色授权
--    运营（2）：菜单 + 全部按钮
--    客服（3）：菜单 + 回复
-- ------------------------------------------------------------
INSERT IGNORE INTO `admin_role_menu` (`role_id`, `menu_id`) VALUES
    (2, 202), (2, 230), (2, 231), (2, 232), (2, 233), (2, 234),
    (3, 202), (3, 230);

-- ============================================================
-- 校验 1：菜单与按钮
-- ============================================================
SELECT id, parent_id, menu_name, menu_type, path, perms, sort_order
FROM admin_menu
WHERE id IN (202, 230, 231, 232, 233, 234)
ORDER BY id;

-- ============================================================
-- 校验 2：角色授权
-- ============================================================
SELECT rm.role_id, rm.menu_id, m.menu_name, m.perms
FROM admin_role_menu rm
JOIN admin_menu m ON m.id = rm.menu_id
WHERE rm.menu_id IN (202, 230, 231, 232, 233, 234)
ORDER BY rm.role_id, rm.menu_id;
