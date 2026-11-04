-- orders.order_status 从字符串迁移到数字（配合 OrderStatusEnum，只执行一次）
--
-- 背景：订单功能落地，order_status 由 VARCHAR 字符串（pending_pay/pending_confirm/...）
--       改为 TINYINT 数字（1待付款 2待确认 3待发货 4已发货 5已完成 6已取消）。
--       代码里 OrderStatusEnum 已经按数字 code 读写，本脚本负责把库里旧字符串数据迁成数字。
--
-- 执行方式（Windows 记得加 --default-character-set=utf8mb4）：
--   服务连的是 Docker MySQL（127.0.0.1:3307），必须打 3307，别打成默认的 3306 本机实例：
--   mysql -h127.0.0.1 -P3307 -uroot -p123456 --default-character-set=utf8mb4 -D e_commerce_db < docs/sql/order-status-migrate.sql
--   （只跑一次；脚本非幂等，重复执行会在「加临时列」处报列已存在）
--
-- 说明：先加临时列做映射，再换正式列，避免 VARCHAR 直接 ALTER 成 TINYINT 时把字符串转数字失败。
--       CASE 的 ELSE 1 是兜底（未收录的字符串/NULL → 1待付款）；执行前已确认库中 52 行均为已知值。
--
-- 历史说明：本脚本执行时 pending_confirm 映射为 2。该状态后来已退役（真实支付场景才需要人工对账，
--           本项目支付是 mock 的），2 已并入 3待发货，历史订单也已迁移；当前状态码见
--           docs/sql/order-status-merge-pending-confirm.sql 与 OrderStatusEnum。
--           本脚本是一次性迁移脚本，不需重跑；此处保留 pending_confirm → 2 的原始映射以备追溯。

-- 1. 加临时列
ALTER TABLE `orders` ADD COLUMN `order_status_tmp` TINYINT NULL;

-- 2. 字符串 → 数字映射（ELSE 1 兜底，保证 NOT NULL 不失败）
UPDATE `orders` SET `order_status_tmp` = CASE `order_status`
    WHEN 'pending_pay' THEN 1
    WHEN 'pending_confirm' THEN 2
    WHEN 'pending_ship' THEN 3
    WHEN 'shipped' THEN 4
    WHEN 'finished' THEN 5
    WHEN 'cancel' THEN 6
    ELSE 1 END;

-- 3. 删旧列 + 把临时列转正
ALTER TABLE `orders` DROP COLUMN `order_status`;
ALTER TABLE `orders` CHANGE COLUMN `order_status_tmp` `order_status` TINYINT NOT NULL DEFAULT 1 COMMENT '订单状态：1待付款 2待确认 3待发货 4已发货 5已完成 6已取消';

-- 4. 自检：列类型应为 tinyint，分布应为 1~6 的数字
SELECT COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'e_commerce_db' AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'order_status';
SELECT order_status, COUNT(*) AS cnt FROM `orders` GROUP BY order_status ORDER BY order_status;
