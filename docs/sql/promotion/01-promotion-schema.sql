-- 促销模块表（product-service 配置 + order-service 算价/使用记录）
-- 执行方式：
--   mysql -u root -p --default-character-set=utf8mb4 e_commerce_db < docs/sql/promotion/01-promotion-schema.sql
--
-- 说明：
--   1. 只建表 / 加列，幂等可重复执行；不初始化、不清空任何业务数据。
--   2. 活动状态不落库：未开始 / 进行中 / 已结束由 start_time、end_time 与当前时间推导，
--      manual_status 只表示运营「提前结束」。所有时间比较由 Java 传入，不用 MySQL NOW()（服务器时区 UTC）。
--   3. 兼容 MySQL 5.7：显式 COLLATE utf8mb4_general_ci，不使用 CHECK 约束。

-- ==================== 促销活动主表 ====================
CREATE TABLE IF NOT EXISTS `promotion` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '活动 ID',
    `name`          VARCHAR(128) NOT NULL                COMMENT '活动名称',
    `type`          VARCHAR(16)  NOT NULL                COMMENT '类型：DIRECT 直减 / FULL_REDUCE 满减',
    `rule_type`     VARCHAR(32)  NOT NULL                COMMENT '玩法：DISCOUNT 限时折扣 / NEW_USER 新用户专享 / FULL_AMOUNT_REDUCE 满额减 / FULL_AMOUNT_DISCOUNT 满额折',
    `target_group`  VARCHAR(16)  NOT NULL DEFAULT 'ALL'  COMMENT '目标人群：ALL 全部用户 / NEW_USER 未下单新用户',
    `scope_type`    VARCHAR(16)  NOT NULL DEFAULT 'PRODUCT' COMMENT '商品范围：ALL 全场 / PRODUCT 指定商品',
    `tag_name`      VARCHAR(32)  DEFAULT NULL            COMMENT 'C 端促销标签，如「限时立减」',
    `start_time`    DATETIME     NOT NULL                COMMENT '开始时间',
    `end_time`      DATETIME     NOT NULL                COMMENT '结束时间',
    `manual_status` TINYINT      NOT NULL DEFAULT 0      COMMENT '运营状态：0 正常 1 提前结束',
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0      COMMENT '软删除：1 已删除 0 正常',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT NULL            COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_promotion_time` (`start_time`, `end_time`),
    KEY `idx_promotion_status` (`is_deleted`, `manual_status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '促销活动';

-- ==================== 活动商品（直减类必填活动价；满减类用来圈定商品范围） ====================
CREATE TABLE IF NOT EXISTS `promotion_item` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `promotion_id`    BIGINT        NOT NULL                COMMENT '活动 ID',
    `product_id`      BIGINT        NOT NULL                COMMENT '商品 ID',
    `promotion_price` DECIMAL(10,2) DEFAULT NULL            COMMENT '活动价（直减类必填；满减类为空）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_promotion_product` (`promotion_id`, `product_id`),
    KEY `idx_promotion_item_product` (`product_id`),
    CONSTRAINT `fk_promotion_item_promotion` FOREIGN KEY (`promotion_id`) REFERENCES `promotion`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_promotion_item_product` FOREIGN KEY (`product_id`) REFERENCES `products`(`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '促销活动商品';

-- ==================== 满减阶梯（满 X 减 Y / 打 Z 折，支持多档） ====================
CREATE TABLE IF NOT EXISTS `promotion_rule` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `promotion_id`     BIGINT        NOT NULL                COMMENT '活动 ID',
    `threshold_amount` DECIMAL(10,2) NOT NULL                COMMENT '满额门槛',
    `discount_amount`  DECIMAL(10,2) DEFAULT NULL            COMMENT '满减金额（满额减用）',
    `discount_rate`    DECIMAL(5,2)  DEFAULT NULL            COMMENT '折扣率（满额折用，如 0.85 表示 85 折）',
    `sort_order`       INT           NOT NULL DEFAULT 0      COMMENT '排序值',
    PRIMARY KEY (`id`),
    KEY `idx_promotion_rule_promotion` (`promotion_id`),
    CONSTRAINT `fk_promotion_rule_promotion` FOREIGN KEY (`promotion_id`) REFERENCES `promotion`(`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '促销满减阶梯';

-- ==================== 促销使用记录（order-service 下单时写入，用于效果统计） ====================
CREATE TABLE IF NOT EXISTS `promotion_usage` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `promotion_id`    BIGINT        NOT NULL                COMMENT '活动 ID',
    `order_id`        BIGINT        NOT NULL                COMMENT '订单 ID',
    `order_no`        VARCHAR(64)   NOT NULL                COMMENT '订单号快照',
    `user_id`         BIGINT        NOT NULL                COMMENT '用户 ID',
    `discount_amount` DECIMAL(10,2) NOT NULL DEFAULT 0      COMMENT '该活动减免金额',
    `create_time`     DATETIME      NOT NULL                COMMENT '记录时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_promotion_usage_order` (`promotion_id`, `order_id`),
    KEY `idx_promotion_usage_user` (`user_id`),
    KEY `idx_promotion_usage_order` (`order_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '促销使用记录';

-- ==================== 订单表补优惠快照 ====================
-- 历史上没有活动，original_amount 允许 NULL；discount_amount 默认 0。
ALTER TABLE `orders`
    ADD COLUMN `original_amount` DECIMAL(10,2) DEFAULT NULL COMMENT '活动前金额（商品原价小计 + 保险）' AFTER `pay_amount`,
    ADD COLUMN `discount_amount` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '优惠金额（直减 + 满减）' AFTER `original_amount`;

-- ==================== 订单项补优惠快照 ====================
ALTER TABLE `order_items`
    ADD COLUMN `original_price` DECIMAL(10,2) DEFAULT NULL COMMENT '原单价快照' AFTER `sales_price`,
    ADD COLUMN `discount_amount` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '该行优惠金额（含满减分摊）' AFTER `sub_total`;
