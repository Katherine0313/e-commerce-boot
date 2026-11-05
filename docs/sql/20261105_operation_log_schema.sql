-- 操作日志审计表（通用基建，所有服务共用）
-- 说明：这张表原先是手工建的，DDL 一直没进仓库，新环境会漏建表。
--       本脚本按生产/本地实际结构回写，供新环境初始化使用。
-- 写入方：common 的 JdbcOperationLogRecorder（JdbcTemplate 直写，异步）
-- 读取方：auth-service 的 AdminOperationLogMapper（后台「操作日志」页面）
-- 显式 COLLATE utf8mb4_general_ci：服务器是 MySQL 5.7，不支持 utf8mb4_0900_ai_ci

CREATE TABLE `operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '操作日志 ID',
  `user_id` bigint DEFAULT NULL COMMENT '操作人 ID（admin.id 或 user.id）',
  `user_type` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作人类型：admin / user',
  `module` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '业务模块：aftersales / product / order …',
  `action` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '动作：REVIEW / DELETE / UPDATE_PRICE …',
  `biz_id` bigint DEFAULT NULL COMMENT '业务对象 ID',
  `detail` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '补充说明（SpEL 求值）',
  `method` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'HTTP 方法',
  `uri` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '请求路径',
  `ip` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '客户端 IP（取 EO-Connecting-IP / X-Forwarded-For 首段）',
  `params` varchar(2000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '请求参数（JSON，超长截断）',
  `success` tinyint NOT NULL DEFAULT '1' COMMENT '是否成功：1 成功 0 失败',
  `error_msg` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '失败原因',
  `cost_ms` bigint DEFAULT NULL COMMENT '耗时（毫秒）',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间（Java 传参）',
  PRIMARY KEY (`id`),
  KEY `idx_operation_log_module` (`module`),
  KEY `idx_operation_log_user` (`user_id`),
  KEY `idx_operation_log_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作日志审计表';
