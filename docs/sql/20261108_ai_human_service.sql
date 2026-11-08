-- AI 客服 · 人工客服（站内转人工）
-- 1) 会话表增加人工模式标记
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chat_conversation' AND COLUMN_NAME = 'human_mode'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE ai_chat_conversation ADD COLUMN human_mode TINYINT NOT NULL DEFAULT 0 COMMENT ''1=已转人工，AI 不再自动回复'' AFTER pinned',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chat_conversation' AND COLUMN_NAME = 'human_at'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE ai_chat_conversation ADD COLUMN human_at DATETIME DEFAULT NULL COMMENT ''转人工时间'' AFTER human_mode',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 后台菜单：人工客服工作台
INSERT INTO admin_menu
  (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status, create_time, update_time)
VALUES
  (1007, 1000, '人工客服', 'C', '/ai/human', NULL, 'ai:human:use', 'ChatDotRound', 7, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), menu_type = VALUES(menu_type),
  path = VALUES(path), perms = VALUES(perms), icon = VALUES(icon),
  sort_order = VALUES(sort_order), visible = VALUES(visible), status = VALUES(status), update_time = NOW();

-- 3) 授予店铺管理员、运营、客服
INSERT IGNORE INTO admin_role_menu (role_id, menu_id)
SELECT r.id, 1007 FROM admin_role r WHERE r.role_key IN ('admin', 'operator', 'service');
