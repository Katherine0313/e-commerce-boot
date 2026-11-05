-- 售后闭环：退款信息、上门取件、换货补发、订单项售后标记。
-- 只加列，不建新表；项目为 mock，退款/取件均为模拟动作。

ALTER TABLE after_sales
    ADD COLUMN refund_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '退款金额快照',
    ADD COLUMN refund_no VARCHAR(64) NULL COMMENT '模拟退款单号',
    ADD COLUMN refund_channel VARCHAR(32) NULL COMMENT '退款渠道（取支付方式）';

ALTER TABLE after_sale_returns
    ADD COLUMN pickup_at DATETIME NULL COMMENT '买家确认上门取件时间',
    ADD COLUMN pickup_no VARCHAR(64) NULL COMMENT '模拟取件单号';

ALTER TABLE after_sale_exchange_returns
    ADD COLUMN pickup_at DATETIME NULL COMMENT '买家确认上门取件时间',
    ADD COLUMN pickup_no VARCHAR(64) NULL COMMENT '模拟取件单号',
    ADD COLUMN ship_courier VARCHAR(64) NULL COMMENT '补发快递公司',
    ADD COLUMN ship_tracking_number VARCHAR(64) NULL COMMENT '补发快递单号',
    ADD COLUMN ship_at DATETIME NULL COMMENT '补发时间';

ALTER TABLE order_items
    ADD COLUMN after_sale_status VARCHAR(32) NOT NULL DEFAULT 'none' COMMENT '售后标记：none/processing/refunded/exchanged/rejected',
    ADD COLUMN after_sale_id BIGINT NULL COMMENT '关联售后单 ID';

CREATE INDEX idx_order_items_after_sale_id ON order_items (after_sale_id);
