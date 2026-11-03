-- ============================================================================
-- 发货管理模块：菜单改造 + 权限码 + 角色授权
--
-- 背景：
--   旧发货管理是「订单管理」下的一个 C 型菜单（id=201，路径 /orders/ship），
--   只有一个「订单发货」按钮（id=220，order:order:ship）。本模块把它升级成
--   顶级「发货管理」目录（201 改成 M 型），下面挂两个子菜单与四个按钮。
--
-- 执行方式（本地库与生产库执行同一份）：
--   mysql -u root -p --default-character-set=utf8mb4 e_commerce_db < docs/sql/rbac/04-delivery-menu.sql
--
-- 说明：
--   1. 幂等：所有 INSERT 都带 NOT EXISTS 判重，可重复执行。
--   2. 不写死新菜单 id：靠 admin_menu.id 的 AUTO_INCREMENT 分配，
--      子项用「父菜单名 + parent_id」反查 id（202/203/204/205… 在库里已被占用）。
--   3. 旧的 220（订单发货 / order:order:ship）保留不动：老接口
--      PUT /api/admin/orders/{id}/ship 还在用这个权限码。
--   4. 改完要重新登录后台才生效（权限在登录时算进 JWT 的 permissions）。
-- ============================================================================

-- ==================== ① 201 改成顶级「发货管理」目录 ====================
-- 原来：parent_id=3（订单管理），C 型菜单，path=/orders/ship，perms=order:order:ship
-- 改后：parent_id=0（顶级），M 型目录，path=/delivery，perms 置空
--        （目录本身不带权限码，权限挂在下面的子菜单/按钮上）
UPDATE `admin_menu`
SET `parent_id` = 0,
    `menu_name` = '发货管理',
    `menu_type` = 'M',
    `path`     = '/delivery',
    `perms`    = NULL,
    `icon`     = 'SendOutlined',
    `sort_order` = 4
WHERE `id` = 201;

-- ==================== ② 顶级菜单顺延排序 ====================
-- 发货管理插在 sort_order=4，售后/库存/经营工具/个人中心/系统管理各往后挪一位
UPDATE `admin_menu`
SET `sort_order` = CASE `id`
    WHEN 4 THEN 5   -- 售后管理
    WHEN 5 THEN 6   -- 库存管理
    WHEN 6 THEN 7   -- 经营工具
    WHEN 7 THEN 8   -- 个人中心
    ELSE 9          -- 系统管理
    END
WHERE `id` IN (4, 5, 6, 7, 8);

-- ==================== ③ 子菜单 ====================
-- 快递发货列表：包裹列表 + 发货 + 补发 + 流水
INSERT INTO `admin_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `perms`, `icon`, `sort_order`)
SELECT 201, '快递发货列表', 'C', '/delivery/list', 'order:delivery:list', NULL, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `admin_menu` WHERE `parent_id` = 201 AND `menu_name` = '快递发货列表');

-- 快递公司：字典列表 + 增改删
INSERT INTO `admin_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `perms`, `icon`, `sort_order`)
SELECT 201, '快递公司', 'C', '/delivery/express', 'order:express:list', NULL, 2
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `admin_menu` WHERE `parent_id` = 201 AND `menu_name` = '快递公司');

-- ==================== ④ 按钮权限 ====================
-- 订单发货（挂在「快递发货列表」下）
INSERT INTO `admin_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `perms`, `icon`, `sort_order`)
SELECT (SELECT `id` FROM `admin_menu` WHERE `parent_id` = 201 AND `menu_name` = '快递发货列表' LIMIT 1),
       '订单发货', 'F', NULL, 'order:delivery:ship', NULL, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `admin_menu` WHERE `perms` = 'order:delivery:ship');

-- 订单补发
INSERT INTO `admin_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `perms`, `icon`, `sort_order`)
SELECT (SELECT `id` FROM `admin_menu` WHERE `parent_id` = 201 AND `menu_name` = '快递发货列表' LIMIT 1),
       '订单补发', 'F', NULL, 'order:delivery:resend', NULL, 2
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `admin_menu` WHERE `perms` = 'order:delivery:resend');

-- 快递公司编辑
INSERT INTO `admin_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `perms`, `icon`, `sort_order`)
SELECT (SELECT `id` FROM `admin_menu` WHERE `parent_id` = 201 AND `menu_name` = '快递公司' LIMIT 1),
       '快递公司编辑', 'F', NULL, 'order:express:save', NULL, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `admin_menu` WHERE `perms` = 'order:express:save');

-- 快递公司删除
INSERT INTO `admin_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `perms`, `icon`, `sort_order`)
SELECT (SELECT `id` FROM `admin_menu` WHERE `parent_id` = 201 AND `menu_name` = '快递公司' LIMIT 1),
       '快递公司删除', 'F', NULL, 'order:express:remove', NULL, 2
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `admin_menu` WHERE `perms` = 'order:express:remove');

-- ==================== ⑤ 角色授权 ====================
-- admin（店铺管理员）：走 *:*:* 超级权限，不依赖 role_menu 明细，不用授权。

-- operator（运营）：发货管理全套（看包裹 / 发货 / 补发 / 字典增删改查）
INSERT IGNORE INTO `admin_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `admin_role` r
         JOIN `admin_menu` m
              ON m.`perms` IN ('order:delivery:list', 'order:delivery:ship', 'order:delivery:resend',
                               'order:express:list', 'order:express:save', 'order:express:remove')
WHERE r.`role_key` = 'operator';

-- warehouse（仓储发货）：发货是仓库的活 —— 看包裹 + 发货 + 补发 + 快递公司下拉
INSERT IGNORE INTO `admin_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `admin_role` r
         JOIN `admin_menu` m
              ON m.`perms` IN ('order:delivery:list', 'order:delivery:ship', 'order:delivery:resend',
                               'order:express:list')
WHERE r.`role_key` = 'warehouse';

-- service（客服）：只读包裹（能查能看流水），不给发货/补发/字典写权限
INSERT IGNORE INTO `admin_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `admin_role` r
         JOIN `admin_menu` m ON m.`perms` = 'order:delivery:list'
WHERE r.`role_key` = 'service';

-- 目录本身（发货管理 201、快递发货列表）也要给到对应角色，
-- 否则菜单管理页里看不到完整的菜单树。
-- 快递公司子菜单带 order:express:list，只给 operator / warehouse（service 不给）。
INSERT IGNORE INTO `admin_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `admin_role` r
         JOIN `admin_menu` m
              ON m.`id` = 201
                  OR (m.`parent_id` = 201 AND m.`perms` = 'order:delivery:list')
WHERE r.`role_key` IN ('operator', 'warehouse', 'service');

INSERT IGNORE INTO `admin_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `admin_role` r
         JOIN `admin_menu` m ON m.`parent_id` = 201 AND m.`perms` = 'order:express:list'
WHERE r.`role_key` IN ('operator', 'warehouse');
