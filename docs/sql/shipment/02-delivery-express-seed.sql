-- ============================================================================
-- 快递公司字典种子数据（delivery_express）
--
-- 来源：order-service 的 CourierEnum（8 家），code 用枚举的英文 key，
-- 与 orders.courier 存的英文 key 一致，mock 物流的 CourierEnum.fromCode 仍能匹配上。
--
-- 执行方式（本地库与生产库执行同一份）：
--   mysql -u root -p --default-character-set=utf8mb4 e_commerce_db < docs/sql/shipment/02-delivery-express-seed.sql
--
-- 说明：
--   1. 幂等：INSERT ... ON DUPLICATE KEY UPDATE，靠 uk_delivery_express_code 去重。
--   2. 只新增/改名，不删数据，也不改 status（管理员在后台自己启停用）。
-- ============================================================================

INSERT INTO `delivery_express` (`code`, `name`, `sort_order`, `status`, `remark`, `is_deleted`, `create_time`, `update_time`)
VALUES
    ('shunfeng',        '顺丰速运', 1, 1, '快递公司字典种子', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('yuantong',        '圆通速递', 2, 1, '快递公司字典种子', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('zhongtong',       '中通快递', 3, 1, '快递公司字典种子', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('yunda',           '韵达快递', 4, 1, '快递公司字典种子', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('shentong',        '申通快递', 5, 1, '快递公司字典种子', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('jitu',            '极兔速递', 6, 1, '快递公司字典种子', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('debang',          '德邦快递', 7, 1, '快递公司字典种子', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('youzhengguonei',  '邮政国内', 8, 1, '快递公司字典种子', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `sort_order` = VALUES(`sort_order`),
    `update_time` = CURRENT_TIMESTAMP;
