-- 系统监控菜单（一级目录，与「系统管理」平级）
-- ID 段 900~907，不影响现有 1~8 与 800~840
-- 本地执行后必须同步生产库（项目硬规则）

INSERT INTO admin_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status, create_time, update_time)
VALUES (900, 0, '系统监控', 'M', NULL, NULL, NULL, 'MonitorOutlined', 10, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), menu_type = VALUES(menu_type),
  path = VALUES(path), component = VALUES(component), perms = VALUES(perms), icon = VALUES(icon),
  sort_order = VALUES(sort_order), visible = VALUES(visible), status = VALUES(status), update_time = NOW();

INSERT INTO admin_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status, create_time, update_time)
VALUES
  (901, 900, '服务监控', 'C', '/monitor/server', NULL, 'monitor:server:list', NULL, 1, 1, 1, NOW(), NOW()),
  (902, 900, '在线用户', 'C', '/monitor/online', NULL, 'monitor:online:list', NULL, 2, 1, 1, NOW(), NOW()),
  (903, 900, '缓存监控', 'C', '/monitor/cache', NULL, 'monitor:cache:list', NULL, 3, 1, 1, NOW(), NOW()),
  (904, 900, '定时任务', 'C', '/monitor/task', NULL, 'monitor:task:list', NULL, 4, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), menu_type = VALUES(menu_type),
  path = VALUES(path), component = VALUES(component), perms = VALUES(perms), icon = VALUES(icon),
  sort_order = VALUES(sort_order), visible = VALUES(visible), status = VALUES(status), update_time = NOW();

INSERT INTO admin_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status, create_time, update_time)
VALUES
  (905, 902, '强制下线', 'F', NULL, NULL, 'monitor:online:logout', NULL, 1, 1, 1, NOW(), NOW()),
  (906, 903, '删除缓存', 'F', NULL, NULL, 'monitor:cache:delete', NULL, 1, 1, 1, NOW(), NOW()),
  (907, 904, '执行任务', 'F', NULL, NULL, 'monitor:task:run', NULL, 1, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), menu_type = VALUES(menu_type),
  path = VALUES(path), component = VALUES(component), perms = VALUES(perms), icon = VALUES(icon),
  sort_order = VALUES(sort_order), visible = VALUES(visible), status = VALUES(status), update_time = NOW();

-- 超管默认可见（*:*:* 本来就不校验菜单），这里仍补上角色关联，保证「角色管理 → 分配菜单」勾选状态正确
INSERT IGNORE INTO admin_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM admin_role r
JOIN admin_menu m ON m.id BETWEEN 900 AND 907
WHERE r.role_key = 'admin';
