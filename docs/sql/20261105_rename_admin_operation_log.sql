-- 后台操作日志表重命名：operation_log → admin_operation_log
-- 原因：该表只服务电商后台管理系统（/api/admin/**），统一加 admin_ 前缀，
--       避免数据库表越来越多时，表和 C 端业务表混在一起难找。
-- 说明：只改表名，不动数据和索引；索引名保持原样（MySQL 索引名按表隔离，不影响使用）。
-- 注意：本地执行后必须在生产执行同一份 SQL；且生产要与新版本后端镜像同批上线，
--       否则旧服务仍在写 operation_log，会因表不存在而写日志失败。

RENAME TABLE `operation_log` TO `admin_operation_log`;
