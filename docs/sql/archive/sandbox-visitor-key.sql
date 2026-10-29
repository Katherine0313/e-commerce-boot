-- 已废弃（2026-10-29 单店化）：多租户沙箱机制已下线，勿在新环境执行。
-- 沙箱复用：shops 加访客标识 + 唯一索引
-- 背景：前端在 localStorage 持久化一个 UUID，作为「同一浏览器」的体验店身份；
--       后端按 visitor_key 命中未过期的沙箱店时复用，不再重复克隆模板店。
-- 执行方式：mysql -u e_commerce_db -p --default-character-set=utf8mb4 e_commerce_db < docs/sql/sandbox-visitor-key.sql
-- 兼容性：MySQL 5.7；显式 utf8mb4 / utf8mb4_general_ci。
-- 幂等：ADD COLUMN 不支持 IF NOT EXISTS，只执行一次。
-- 顺序：先执行本脚本，再发布 shop-service（否则新代码 INSERT 会报 Unknown column 'visitor_key'）。

-- 前置检查：应为空，有输出说明已执行过，不要再跑 ALTER
SELECT COLUMN_NAME
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'shops'
   AND COLUMN_NAME = 'visitor_key';

ALTER TABLE `shops`
    ADD COLUMN `visitor_key` VARCHAR(64)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
        DEFAULT NULL
        COMMENT '访客标识（前端 localStorage 的 UUID；仅沙箱店使用，模板店/历史店为 NULL）'
        AFTER `expires_at`,
    -- 同一 visitor_key 只允许一条店铺记录；唯一索引允许多个 NULL，历史数据不受影响
    ADD UNIQUE KEY `uk_shops_visitor_key` (`visitor_key`);

-- 自检
SHOW CREATE TABLE `shops`;
