-- 数据库监控：慢 SQL 记录表 + 菜单/按钮权限 + 清理任务注册
-- 本地执行后必须同步生产库（项目硬规则）
-- 脚本可重复执行（CREATE TABLE IF NOT EXISTS / ON DUPLICATE KEY UPDATE / INSERT IGNORE）

-- 1) 慢 SQL 监控记录表：各业务服务的 MyBatis 拦截器只写超过阈值的语句
--    不存参数值，sql_text 只保留归一化后的 ? 占位，聚合靠 sql_fingerprint
CREATE TABLE IF NOT EXISTS `admin_db_slow_sql` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `service_name` varchar(64) NOT NULL COMMENT '服务名，如 product-service',
  `mapper_method` varchar(255) NOT NULL COMMENT 'MappedStatement id，定位到 Mapper 方法',
  `sql_type` varchar(16) DEFAULT NULL COMMENT 'SELECT/INSERT/UPDATE/DELETE',
  `sql_fingerprint` char(32) NOT NULL COMMENT '归一化 SQL 的 MD5，用于聚合',
  `sql_text` text COMMENT '归一化 SQL，截断 2000 字符，不含参数值',
  `cost_ms` int NOT NULL COMMENT 'SQL 执行耗时（毫秒）',
  `row_count` int DEFAULT NULL COMMENT '返回/影响行数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_fingerprint` (`sql_fingerprint`),
  KEY `idx_service_created` (`service_name`, `created_at`),
  KEY `idx_cost` (`cost_ms`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='慢 SQL 监控记录';

-- 2) 菜单：数据库监控（挂「系统监控」id=900 下，排序第 5）
INSERT INTO admin_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status, create_time, update_time)
VALUES (921, 900, '数据库监控', 'C', '/monitor/database', NULL, 'monitor:db:list', NULL, 5, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), menu_type = VALUES(menu_type),
  path = VALUES(path), component = VALUES(component), perms = VALUES(perms), icon = VALUES(icon),
  sort_order = VALUES(sort_order), visible = VALUES(visible), status = VALUES(status), update_time = NOW();

-- 3) 按钮：清空历史（页面按钮必须显式绑定权限指令）
INSERT INTO admin_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status, create_time, update_time)
VALUES (922, 921, '清空历史', 'F', NULL, NULL, 'monitor:db:clear', NULL, 1, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), menu_type = VALUES(menu_type),
  path = VALUES(path), component = VALUES(component), perms = VALUES(perms), icon = VALUES(icon),
  sort_order = VALUES(sort_order), visible = VALUES(visible), status = VALUES(status), update_time = NOW();

-- 4) 超管授权（*:*:* 本就不校验菜单，这里补关联保证「角色管理」勾选状态正确）
INSERT IGNORE INTO admin_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM admin_role r
JOIN admin_menu m ON m.id IN (921, 922)
WHERE r.role_key = 'admin';

-- 5) 清理任务注册：只注册在 system-service（task_group 只影响手动执行的转发目标）
--    task_name 必须与 DatabaseSlowSqlCleanupTaskRunner#taskName 和 @MonitoredTask#name 一致
INSERT INTO admin_monitor_task
  (task_name, task_group, bean_name, method_name, task_type, schedule_value, status, concurrent, remark)
VALUES
  ('dbSlowSqlCleanup', 'system-service', 'databaseSlowSqlCleanupTaskRunner', 'clean', 1, '0 20 3 * * ?', 1, 1, '清理超过保留期（默认 7 天）的慢 SQL 记录')
ON DUPLICATE KEY UPDATE
  task_group = VALUES(task_group), bean_name = VALUES(bean_name), method_name = VALUES(method_name),
  task_type = VALUES(task_type), schedule_value = VALUES(schedule_value), status = VALUES(status),
  concurrent = VALUES(concurrent), remark = VALUES(remark), update_time = NOW();
