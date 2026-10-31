-- PC 商城登录/注册：为 C 端 user 表补充用户名与密码列
--
-- 执行方式：
--   mysql -u root -p --default-character-set=utf8mb4 e_commerce_db < docs/sql/user-pc-auth.sql
--
-- 说明：
--   1. 只新增列和索引，不修改/删除已有数据。
--   2. MySQL 5.7 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报「已存在」，忽略即可。
--   3. phone 历史数据可能有重复（小程序未强制唯一），因此这里不加唯一索引，注册查重由服务层处理。
--   4. username 唯一索引用的是表默认 collation（utf8mb4_general_ci，大小写不敏感），
--      所以 Jamietest 和 jamietest 会被视为同一个用户名；应用层注册时统一转小写存储。
--
-- 状态：2026-10-31 已在本地与生产库执行完毕。

-- ==================== 列 ====================

-- 用户名：PC 端账号密码登录用；小程序用户为 NULL
ALTER TABLE `user`
    ADD COLUMN `username` VARCHAR(64) DEFAULT NULL COMMENT 'PC 商城登录用户名（全局唯一，转小写存储）' AFTER `name`;

-- 密码：BCrypt 密文
ALTER TABLE `user`
    ADD COLUMN `password` VARCHAR(255) DEFAULT NULL COMMENT 'PC 端登录密码（BCrypt）' AFTER `phone`;

-- ==================== 索引 ====================

-- 用户名唯一：登录名不能重复（历史数据都是 NULL，MySQL 唯一索引允许多个 NULL）
ALTER TABLE `user`
    ADD UNIQUE INDEX `uk_user_username` (`username`);

-- 手机号普通索引：验证码登录按手机号查；历史数据可能重复，故不建唯一索引
ALTER TABLE `user`
    ADD INDEX `idx_user_phone` (`phone`);

-- ==================== 验证 ====================
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user'
--    AND COLUMN_NAME IN ('username', 'password');
-- SHOW INDEX FROM `user`;
