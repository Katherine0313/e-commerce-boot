-- operation_log 增量改造
-- 1) username：操作人账号快照。账号软删（admin.is_deleted=1）或改名后，只靠 user_id 认不出操作人
-- 2) detail 扩到 1024：要放「改前 → 改后」的变更文案，255 不够用
-- 3) 复合索引：列表页按「模块 + 动作 + 时间」筛选；按「账号 + 时间」筛选
-- 注意：本地执行后必须在生产执行同一份 SQL（不允许只改本地）

ALTER TABLE `operation_log`
  ADD COLUMN `username` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作人账号快照' AFTER `user_id`;

ALTER TABLE `operation_log`
  MODIFY COLUMN `detail` varchar(1024) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '补充说明/变更详情';

ALTER TABLE `operation_log`
  ADD INDEX `idx_operation_log_module_action` (`module`, `action`, `created_at`),
  ADD INDEX `idx_operation_log_username` (`username`, `created_at`);
