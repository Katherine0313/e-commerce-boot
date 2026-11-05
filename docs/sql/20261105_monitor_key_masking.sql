-- 缓存监控：查看完整 Redis Key 的独立权限
-- 列表接口只返回脱敏 key（保留前缀、掩码随机值），避免后台页面直接铺开全量 key 名；
-- 需要完整 key 时再点"查看"，该动作单独授权并写操作日志。
-- 本地执行后必须同步生产库（项目硬规则）

INSERT INTO admin_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status, create_time, update_time)
VALUES (911, 903, '查看完整Key', 'F', NULL, NULL, 'monitor:cache:key:view', NULL, 2, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), menu_type = VALUES(menu_type),
  path = VALUES(path), component = VALUES(component), perms = VALUES(perms), icon = VALUES(icon),
  sort_order = VALUES(sort_order), visible = VALUES(visible), status = VALUES(status), update_time = NOW();

-- 超管默认可见（*:*:* 本来就不校验菜单），补上关联保证「角色管理 → 分配菜单」勾选状态正确
INSERT IGNORE INTO admin_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM admin_role r
JOIN admin_menu m ON m.id = 911
WHERE r.role_key = 'admin';
