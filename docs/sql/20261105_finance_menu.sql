-- 财务管理菜单（一级目录，排在「经营工具」之后）
-- ID 段 912~920；原 7 / 8 / 900 的 sort_order 顺延为 9 / 10 / 11
-- 本地执行后必须同步生产库（项目硬规则）

INSERT INTO admin_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status, create_time, update_time)
VALUES (912, 0, '财务管理', 'M', NULL, NULL, NULL, 'AccountBookOutlined', 8, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), menu_type = VALUES(menu_type),
  path = VALUES(path), component = VALUES(component), perms = VALUES(perms), icon = VALUES(icon),
  sort_order = VALUES(sort_order), visible = VALUES(visible), status = VALUES(status), update_time = NOW();

INSERT INTO admin_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status, create_time, update_time)
VALUES
  (913, 912, '收款单', 'C', '/finance/receipt-list', NULL, 'finance:receipt:list', NULL, 1, 1, 1, NOW(), NOW()),
  (914, 912, '收款账户', 'C', '/finance/account-list', NULL, 'finance:account:list', NULL, 2, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), menu_type = VALUES(menu_type),
  path = VALUES(path), component = VALUES(component), perms = VALUES(perms), icon = VALUES(icon),
  sort_order = VALUES(sort_order), visible = VALUES(visible), status = VALUES(status), update_time = NOW();

INSERT INTO admin_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status, create_time, update_time)
VALUES
  (915, 913, '查看收款单', 'F', NULL, NULL, 'finance:receipt:detail', NULL, 1, 1, 1, NOW(), NOW()),
  (916, 913, '审核收款单', 'F', NULL, NULL, 'finance:receipt:audit', NULL, 2, 1, 1, NOW(), NOW()),
  (917, 913, '导出收款单', 'F', NULL, NULL, 'finance:receipt:export', NULL, 3, 1, 1, NOW(), NOW()),
  (918, 914, '新增收款账户', 'F', NULL, NULL, 'finance:account:add', NULL, 1, 1, 1, NOW(), NOW()),
  (919, 914, '编辑收款账户', 'F', NULL, NULL, 'finance:account:edit', NULL, 2, 1, 1, NOW(), NOW()),
  (920, 914, '删除收款账户', 'F', NULL, NULL, 'finance:account:remove', NULL, 3, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), menu_type = VALUES(menu_type),
  path = VALUES(path), component = VALUES(component), perms = VALUES(perms), icon = VALUES(icon),
  sort_order = VALUES(sort_order), visible = VALUES(visible), status = VALUES(status), update_time = NOW();

-- 新一级菜单插在「经营工具」之后，原后续菜单顺延
UPDATE admin_menu SET sort_order = 9  WHERE id = 7;
UPDATE admin_menu SET sort_order = 10 WHERE id = 8;
UPDATE admin_menu SET sort_order = 11 WHERE id = 900;

-- 超管默认授权（*:*:* 本来就不校验菜单，这里补上保证「角色管理 → 分配菜单」勾选状态正确）
INSERT IGNORE INTO admin_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM admin_role r
JOIN admin_menu m ON m.id BETWEEN 912 AND 920
WHERE r.role_key = 'admin';
