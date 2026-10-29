-- 订单域表（order-service，对应 order 模块：订单 / 订单项 / 购物车 / 收货地址）
-- 数据来源：e-commerce/server/sqlite/database.sqlite（Node 后端模板店「杰米小店」）。
--
-- 执行方式（任选其一）：
--   mysql -u root -p e_commerce_db < docs/sql/order.sql
-- 或在 MySQL 客户端里 source docs/sql/order.sql
--
-- 说明：本脚本只「建表」，幂等可重复执行（CREATE TABLE IF NOT EXISTS），不清空/重建已有表。
--       数据迁移（INSERT）单独在迁移脚本里做，本文件不落数据。
--
-- SQLite → MySQL 的几处重设计（延续 docs/sql/product.sql 的规则）：
--   1. id 用 BIGINT AUTO_INCREMENT，去掉 SQLite 的 ID_BLOCK 号段偏移 hack。
--   2. 时间戳 INTEGER（Unix 秒）→ DATETIME（迁移数据用 FROM_UNIXTIME 内联转换）。
--   3. 价格/金额 REAL → DECIMAL(10,2)。
--   4. 时间字段命名用原表 _at 风格（created_at/updated_at/pay_at/...），
--      有意与商品域（create_time/update_time）不同——订单本身是密集的事件时间戳表，
--      且原 sqlite 系统全表统一 _at，还原它比强行对齐 _time 更自然。
--   5. 加物理外键（延续商品域「DB 兜底级联」的决策）：
--        order_items.order_id -> orders.id          ON DELETE CASCADE（原表就有）
--        cart.product_id      -> products.id        ON DELETE CASCADE（原表就有）
--        cart.user_id         -> user.id            ON DELETE CASCADE（用户决策新增）
--        address.user_id -> user.id                ON DELETE CASCADE（用户决策新增）
--        orders.user_id       -> user.id            ON DELETE RESTRICT（订单是重要数据，不随用户删）
--      ⚠️ order_items.product_id 故意【不加】外键：原表本就没有；订单项是下单时的商品快照
--        （已冗余存 product_name/image/sales_price），商品日后删除时订单历史仍要可读。
--        迁移数据里那条 product_id=41 的孤儿订单项（指向已被原系统删除的商品）【直接丢弃，不迁移】。
--   6. 排序规则显式用 utf8mb4_general_ci（MySQL 5.7 也支持），
--      不用 MySQL 8 默认的 utf8mb4_0900_ai_ci，保证导出后能导入服务器。

-- ==================== 订单 ====================
CREATE TABLE IF NOT EXISTS `orders` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '订单 ID',
    `user_id`        BIGINT        NOT NULL                COMMENT '下单用户 ID',
    `order_no`       VARCHAR(64)   NOT NULL                COMMENT '订单号（业务唯一，ORD+时间戳）',
    `is_deleted`     TINYINT       NOT NULL DEFAULT 0      COMMENT '软删除：1 已删除 0 正常',
    `pay_amount`     DECIMAL(10,2) DEFAULT NULL            COMMENT '订单金额（下单时 = 商品小计 + 保险；支付后即实付金额）',
    `pay_type`       VARCHAR(32)   DEFAULT NULL            COMMENT '支付方式：WeChat/Alipay/BankCard',
    `order_status`   TINYINT       NOT NULL DEFAULT 1      COMMENT '订单状态：1待付款 2待确认 3待发货 4已发货 5已完成 6已取消',
    `remark`         VARCHAR(512)  DEFAULT NULL            COMMENT '买家备注',
    `consignee`      VARCHAR(64)   DEFAULT NULL            COMMENT '收货人姓名快照',
    `phone`          VARCHAR(32)   DEFAULT NULL            COMMENT '收货人手机号快照',
    `region`         VARCHAR(255)  DEFAULT NULL            COMMENT '省市区快照',
    `detail_address` VARCHAR(512)  DEFAULT NULL            COMMENT '详细地址快照',
    `shipping_type`  VARCHAR(64)   NOT NULL DEFAULT '商家配送' COMMENT '配送方式（原 sqlite 默认值乱码，已还原为「商家配送」）',
    `courier`        VARCHAR(64)   DEFAULT NULL            COMMENT '快递公司',
    `tracking_number` VARCHAR(64)  DEFAULT NULL            COMMENT '快递单号',
    `sign_status`    VARCHAR(32)   NOT NULL DEFAULT 'unsigned' COMMENT '签收状态：signed 用户签收 / auto 系统自动 / unsigned 未签收',
    `is_subscribe_unpaid` TINYINT   NOT NULL DEFAULT 0      COMMENT '是否授权未支付提醒：1是 0否',
    `unpaid_notify_sent`  TINYINT   NOT NULL DEFAULT 0      COMMENT '未支付提醒是否已推送：1是 0否',
    `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    `pay_at`         DATETIME      DEFAULT NULL            COMMENT '支付时间',
    `ship_at`        DATETIME      DEFAULT NULL            COMMENT '发货时间',
    `confirm_at`     DATETIME      DEFAULT NULL            COMMENT '确认收货时间',
    `finish_at`      DATETIME      DEFAULT NULL            COMMENT '完成时间',
    `updated_at`     DATETIME      DEFAULT NULL            COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_orders_order_no` (`order_no`),
    KEY `idx_orders_status_created` (`order_status`, `created_at`),
    KEY `idx_orders_unpaid_notify` (`order_status`, `is_subscribe_unpaid`, `unpaid_notify_sent`, `created_at`),
    CONSTRAINT `fk_orders_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '订单';

-- ==================== 订单项 ====================
CREATE TABLE IF NOT EXISTS `order_items` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '订单项 ID',
    `order_id`         BIGINT        NOT NULL                COMMENT '所属订单 ID',
    `product_id`       BIGINT        NOT NULL                COMMENT '商品 ID（下单快照，订单历史不随商品删除，故不加外键）',
    `product_name`     VARCHAR(255)  NOT NULL                COMMENT '商品名快照',
    `image`            VARCHAR(512)  DEFAULT NULL            COMMENT '商品图快照',
    `sales_price`      DECIMAL(10,2) NOT NULL                COMMENT '成交单价',
    `quantity`         INT           NOT NULL                COMMENT '购买数量',
    `sub_total`        DECIMAL(10,2) NOT NULL                COMMENT '小计 = 单价 × 数量',
    `insurance_detail` VARCHAR(512)  DEFAULT '[]'            COMMENT '保险详情 JSON（历史遗留，现为 "[]"）',
    `insurance_fee`    DECIMAL(10,2) NOT NULL DEFAULT 0      COMMENT '保险费',
    `is_deleted`       TINYINT       NOT NULL DEFAULT 0      COMMENT '软删除：1 已删除 0 正常',
    `created_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_order_items_order` FOREIGN KEY (`order_id`) REFERENCES `orders`(`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '订单项';

-- ==================== 购物车 ====================
CREATE TABLE IF NOT EXISTS `cart` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '购物车项 ID',
    `user_id`          BIGINT        NOT NULL                COMMENT '用户 ID',
    `product_id`       BIGINT        NOT NULL                COMMENT '商品 ID',
    `quantity`         INT           NOT NULL DEFAULT 1      COMMENT '数量',
    `name`             VARCHAR(255)  NOT NULL DEFAULT ''     COMMENT '商品名快照',
    `image`            VARCHAR(512)  DEFAULT NULL            COMMENT '商品图快照',
    `price`            DECIMAL(10,2) NOT NULL DEFAULT 0      COMMENT '单价快照',
    `description`      TEXT                                  COMMENT '商品描述快照',
    `insurance_detail` VARCHAR(512)  DEFAULT '[]'            COMMENT '保险详情 JSON（历史遗留，现为 "[]"）',
    `insurance_fee`    DECIMAL(10,2) NOT NULL DEFAULT 0      COMMENT '保险费',
    `created_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME      DEFAULT NULL            COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cart_user_product` (`user_id`, `product_id`),
    CONSTRAINT `fk_cart_user`    FOREIGN KEY (`user_id`)    REFERENCES `user`(`id`)     ON DELETE CASCADE,
    CONSTRAINT `fk_cart_product` FOREIGN KEY (`product_id`) REFERENCES `products`(`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '购物车';

-- ==================== 收货地址 ====================
CREATE TABLE IF NOT EXISTS `address` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '地址 ID',
    `user_id`        BIGINT       NOT NULL                COMMENT '用户 ID',
    `consignee`      VARCHAR(64)  NOT NULL                COMMENT '收货人姓名',
    `phone`          VARCHAR(32)  NOT NULL                COMMENT '收货人手机号',
    `region`         VARCHAR(255) NOT NULL                COMMENT '省市区',
    `detail_address` VARCHAR(512) NOT NULL                COMMENT '详细地址',
    `is_default`     TINYINT      NOT NULL DEFAULT 0      COMMENT '是否默认地址：1 是 0 否',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     DEFAULT NULL            COMMENT '更新时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_address_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '收货地址';
