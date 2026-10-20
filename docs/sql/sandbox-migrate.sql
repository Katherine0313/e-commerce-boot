-- 多租户沙箱迁移：user 表加 shop_id，C 端用户按店隔离
-- 执行方式：mysql -u e_commerce_db -p --default-character-set=utf8mb4 e_commerce_db < docs/sql/sandbox-migrate.sql
-- 说明：幂等不保证（ADD COLUMN 重复执行会报错），只执行一次；执行前请确认无并发的 C 端写入。

-- 1. user 表加 shop_id（现有数据都是模板店，默认补 1）
ALTER TABLE `user`
    ADD COLUMN `shop_id` BIGINT NOT NULL DEFAULT 1 COMMENT '所属店铺（模板店=1）' AFTER `id`;

-- 2. 唯一键 openid → (shop_id, openid)：同一 openid 在不同店铺是不同用户
ALTER TABLE `user` DROP INDEX `uk_openid`;
ALTER TABLE `user` ADD UNIQUE KEY `uk_shop_openid` (`shop_id`, `openid`);
