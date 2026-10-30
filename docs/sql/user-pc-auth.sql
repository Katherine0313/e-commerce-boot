-- PC 商城登录/注册：为 C 端 user 表补充密码列
--
-- 执行方式：
--   mysql -u root -p --default-character-set=utf8mb4 e_commerce_db < docs/sql/user-pc-auth.sql
--
-- 说明：
--   1. 只新增列和普通索引，不修改/删除已有数据。
--   2. MySQL 5.7 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报「已存在」，忽略即可。
--   3. phone 历史数据可能有重复（小程序未强制唯一），因此这里不加唯一索引，注册查重由服务层处理。

ALTER TABLE `user`
    ADD COLUMN `password` VARCHAR(255) DEFAULT NULL COMMENT 'PC 端登录密码（BCrypt）' AFTER `phone`;

ALTER TABLE `user`
    ADD INDEX `idx_user_phone` (`phone`);
