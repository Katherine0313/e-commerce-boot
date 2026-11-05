-- 日志菜单与权限种子
-- 803「操作日志」已存在（parent_id=8, path=/system/logs, perms=system:log:list），直接复用，不重复插入。
-- 本脚本新增：
--   804 登录日志页面（parent=8 系统管理）
--   839 操作日志导出按钮（parent=803）
--   840 登录日志导出按钮（parent=804）
-- 说明：
--   - id 839/840 之前空闲（admin_menu MAX(id)=838），执行前请再确认一次 MAX(id) 避免冲突
--   - F 类型按钮不出现在侧边栏（后端 buildMenuTree 已排除），path 留 NULL
--   - 只授权给超管（role_key='admin'）
-- 注意：本地执行后必须在生产执行同一份 SQL（不允许只改本地）

INSERT INTO `admin_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `perms`, `icon`, `sort_order`, `status`, `visible`)
VALUES (804, 8, '登录日志', 'C', '/system/logs/login', 'system:log:login:list', 'icon-login', 5, 1, 1);

INSERT INTO `admin_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `perms`, `sort_order`, `status`, `visible`) VALUES
(839, 803, '日志导出', 'F', 'system:log:export', 1, 1, 1),
(840, 804, '日志导出', 'F', 'system:log:login:export', 1, 1, 1);

-- 超管授权
INSERT INTO `admin_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `admin_role` r
JOIN `admin_menu` m ON m.`id` IN (804, 839, 840)
WHERE r.`role_key` = 'admin';
