-- 系统管理：数据库管理 / 接口管理 —— 建表 + 菜单权限种子
-- 适用：生产 MySQL 5.7，utf8mb4_general_ci
-- 幂等：可重复执行；本地执行后必须在生产执行同一份 SQL
-- 说明：本地执行前 admin_menu MAX(id)=1017，850~860 区间空闲

CREATE TABLE IF NOT EXISTS `admin_db_backup` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `file_name` varchar(160) COLLATE utf8mb4_general_ci NOT NULL COMMENT '备份文件全名（含分卷号）',
  `part` int NOT NULL DEFAULT '1' COMMENT '分卷号',
  `size_bytes` bigint NOT NULL DEFAULT '0' COMMENT '压缩后大小',
  `compressed` tinyint NOT NULL DEFAULT '1' COMMENT '是否 gzip 压缩',
  `table_count` int NOT NULL DEFAULT '0' COMMENT '本次备份表数量',
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'SUCCESS' COMMENT 'RUNNING/SUCCESS/FAILED',
  `created_by` bigint DEFAULT NULL COMMENT '发起人 admin.id',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_name` (`file_name`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据库备份记录';

CREATE TABLE IF NOT EXISTS `admin_api_doc` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `service_name` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '服务名，如 auth-service',
  `group_name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '分组（OpenAPI tag 或路径首段）',
  `api_name` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '人工维护的中文名',
  `http_method` varchar(10) COLLATE utf8mb4_general_ci NOT NULL,
  `path` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `summary` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'OpenAPI summary（同步字段）',
  `description` text COLLATE utf8mb4_general_ci COMMENT 'OpenAPI description（同步字段）',
  `require_perm` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所需权限码，来自 x-require-perm',
  `is_public` tinyint NOT NULL DEFAULT '0' COMMENT '人工维护：是否公共接口',
  `query_params` json DEFAULT NULL COMMENT 'query/path 参数数组',
  `header_params` json DEFAULT NULL COMMENT 'header 参数数组',
  `body_params` json DEFAULT NULL COMMENT '请求体 schema',
  `response_params` json DEFAULT NULL COMMENT '响应 schema',
  `response_example` json DEFAULT NULL COMMENT '人工维护：返回示例',
  `error_codes` json DEFAULT NULL COMMENT '人工维护：错误码',
  `missing` tinyint NOT NULL DEFAULT '0' COMMENT '1=本次同步未出现，待人工确认',
  `sync_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_method_path` (`service_name`, `http_method`, `path`),
  KEY `idx_service_group` (`service_name`, `group_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='接口文档（OpenAPI 同步 + 人工补充）';

-- 菜单：850 数据库管理、851 接口管理（parent_id=8 系统管理）
INSERT INTO `admin_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `sort_order`, `visible`, `status`)
VALUES
  (850, 8, '数据库管理', 'C', '/system/database', NULL, 'system:db:list', 'DatabaseOutlined', 6, 1, 1),
  (851, 8, '接口管理', 'C', '/system/api', NULL, 'system:api:list', 'ApiOutlined', 7, 1, 1)
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`), `menu_name` = VALUES(`menu_name`), `menu_type` = VALUES(`menu_type`),
  `path` = VALUES(`path`), `component` = VALUES(`component`), `perms` = VALUES(`perms`),
  `icon` = VALUES(`icon`), `sort_order` = VALUES(`sort_order`), `visible` = VALUES(`visible`),
  `status` = VALUES(`status`), `update_time` = NOW();

-- 按钮权限（第一版 P0 范围）
INSERT INTO `admin_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `perms`, `sort_order`, `visible`, `status`)
VALUES
  (852, 850, '发起备份', 'F', 'system:db:backup', 1, 1, 1),
  (853, 850, '优化表', 'F', 'system:db:optimize', 2, 1, 1),
  (854, 850, '下载备份', 'F', 'system:db:download', 3, 1, 1),
  (855, 850, '删除备份', 'F', 'system:db:delete', 4, 1, 1),
  (856, 851, '同步接口', 'F', 'system:api:sync', 1, 1, 1),
  (857, 851, '编辑接口', 'F', 'system:api:edit', 2, 1, 1)
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`), `menu_name` = VALUES(`menu_name`), `menu_type` = VALUES(`menu_type`),
  `perms` = VALUES(`perms`), `sort_order` = VALUES(`sort_order`), `visible` = VALUES(`visible`),
  `status` = VALUES(`status`), `update_time` = NOW();

-- 超管授权
INSERT IGNORE INTO `admin_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `admin_role` r
JOIN `admin_menu` m ON m.`id` IN (850, 851, 852, 853, 854, 855, 856, 857)
WHERE r.`role_key` = 'admin';
