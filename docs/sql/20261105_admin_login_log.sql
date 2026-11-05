-- 后台管理员登录/登出日志
-- 为什么独立建表：admin 表没有 last_login_time / login_ip 之类字段，
--   登录失败计数在 Redis（key: auth:login:fail:{username}:{ip}），不留审计痕迹，
--   账号爆破、误锁定、账号停用都查不到。
-- 只记后台管理员（admin 令牌），C 端用户登录不记。
-- 表名前缀 admin_ 与既有 RBAC 表（admin / admin_role / admin_menu / admin_role_menu）保持一致。

CREATE TABLE `admin_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `admin_id` bigint DEFAULT NULL COMMENT '管理员 ID，账号不存在时为空',
  `username` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '登录名（原样保留，便于排查爆破）',
  `login_type` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'LOGIN / LOGOUT',
  `result_code` varchar(24) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'SUCCESS / BAD_CREDENTIALS / USER_DISABLED / CAPTCHA_ERROR / LOCKED / PARAM_MISSING',
  `reason` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '失败原因文本',
  `ip` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '客户端 IP',
  `user_agent` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'User-Agent',
  `created_at` datetime NOT NULL COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_login_log_username` (`username`, `created_at`),
  KEY `idx_login_log_created` (`created_at`),
  KEY `idx_login_log_ip` (`ip`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='后台登录/登出日志';
