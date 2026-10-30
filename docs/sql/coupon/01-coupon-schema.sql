-- 优惠券模块（order-service：券模板 + 用户券 + 核销）
-- 执行方式：
--   mysql -u root -p --default-character-set=utf8mb4 e_commerce_db < docs/sql/coupon/01-coupon-schema.sql
--
-- 说明：
--   1. 只建表 / 加列，幂等可重复执行；不初始化、不清空业务数据。
--   2. 券模板状态不落库：未开始/发放中/已结束由 receive_start、receive_end、remain_quantity 推导；
--      manual_status=1 表示运营停止发放。
--   3. 用户券过期不落状态：UNUSED 且 end_time < now 即为已过期，由 Java 推导。
--   4. 兼容 MySQL 5.7：显式 COLLATE utf8mb4_general_ci。

-- ==================== 券模板 ====================
CREATE TABLE IF NOT EXISTS `coupon_template` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '券模板 ID',
    `name`             VARCHAR(128)  NOT NULL                COMMENT '券名称',
    `type`             VARCHAR(16)   NOT NULL                COMMENT '类型：FIXED 满减 / DISCOUNT 折扣',
    `face_value`       DECIMAL(10,2) DEFAULT NULL            COMMENT '满减金额（满减券用）',
    `discount_rate`    DECIMAL(5,2)  DEFAULT NULL            COMMENT '折扣率（折扣券用，0.85 = 85 折）',
    `max_discount`     DECIMAL(10,2) DEFAULT NULL            COMMENT '折扣券最高优惠封顶（空=不封顶）',
    `threshold_amount` DECIMAL(10,2) NOT NULL DEFAULT 0      COMMENT '使用门槛，0 = 无门槛',
    `scope_type`       VARCHAR(16)   NOT NULL DEFAULT 'ALL'  COMMENT '适用范围：ALL 全场 / CATEGORY 分类 / PRODUCT 商品',
    `target_group`     VARCHAR(16)   NOT NULL DEFAULT 'ALL'  COMMENT '目标人群：ALL 全部 / NEW_USER 未下单新用户',
    `total_quantity`   INT           NOT NULL                COMMENT '发行总量',
    `remain_quantity`  INT           NOT NULL                COMMENT '剩余可领数量',
    `per_user_limit`   INT           NOT NULL DEFAULT 1      COMMENT '每人限领（v1 固定 1，靠唯一键兜底）',
    `receive_start`    DATETIME      NOT NULL                COMMENT '领取开始时间',
    `receive_end`      DATETIME      NOT NULL                COMMENT '领取结束时间',
    `valid_type`       VARCHAR(16)   NOT NULL DEFAULT 'FIXED' COMMENT '有效期类型：FIXED 固定时段 / RECEIVE_DAYS 领取后 N 天',
    `valid_start`      DATETIME      DEFAULT NULL            COMMENT '固定有效期开始（FIXED 用）',
    `valid_end`        DATETIME      DEFAULT NULL            COMMENT '固定有效期结束（FIXED 用）',
    `valid_days`       INT           DEFAULT NULL            COMMENT '领取后有效天数（RECEIVE_DAYS 用）',
    `manual_status`    TINYINT       NOT NULL DEFAULT 0      COMMENT '运营状态：0 正常 1 停止发放',
    `is_deleted`       TINYINT       NOT NULL DEFAULT 0      COMMENT '软删除：1 已删除 0 正常',
    `description`      VARCHAR(255)  DEFAULT NULL            COMMENT '使用说明',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME      DEFAULT NULL            COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_coupon_receive` (`receive_start`, `receive_end`),
    KEY `idx_coupon_status` (`is_deleted`, `manual_status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '优惠券模板';

-- ==================== 券适用范围（分类 / 商品） ====================
CREATE TABLE IF NOT EXISTS `coupon_scope` (
    `id`                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `coupon_template_id` BIGINT      NOT NULL                COMMENT '券模板 ID',
    `target_type`        VARCHAR(16) NOT NULL                COMMENT '范围类型：CATEGORY 分类 / PRODUCT 商品',
    `target_id`          BIGINT      NOT NULL                COMMENT '分类 ID 或商品 ID',
    PRIMARY KEY (`id`),
    KEY `idx_coupon_scope_template` (`coupon_template_id`),
    KEY `idx_coupon_scope_target` (`target_type`, `target_id`),
    CONSTRAINT `fk_coupon_scope_template` FOREIGN KEY (`coupon_template_id`) REFERENCES `coupon_template`(`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '优惠券适用范围';

-- ==================== 用户券实例 ====================
CREATE TABLE IF NOT EXISTS `user_coupon` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '用户券 ID',
    `code`          VARCHAR(32) NOT NULL                COMMENT '券码（客服查询用）',
    `template_id`   BIGINT      NOT NULL                COMMENT '券模板 ID',
    `user_id`       BIGINT      NOT NULL                COMMENT '用户 ID',
    `status`        VARCHAR(16) NOT NULL DEFAULT 'UNUSED' COMMENT '状态：UNUSED 未使用 / LOCKED 下单锁定 / USED 已使用',
    `receive_time`  DATETIME    NOT NULL                COMMENT '领取时间',
    `start_time`    DATETIME    NOT NULL                COMMENT '可用开始时间',
    `end_time`      DATETIME    NOT NULL                COMMENT '可用结束时间',
    `order_id`      BIGINT      DEFAULT NULL            COMMENT '锁定/使用的订单 ID',
    `lock_time`     DATETIME    DEFAULT NULL            COMMENT '锁定时间',
    `used_time`     DATETIME    DEFAULT NULL            COMMENT '使用时间',
    `source`        VARCHAR(16) NOT NULL DEFAULT 'RECEIVE' COMMENT '来源：RECEIVE 领取 / ADMIN 后台发放',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_coupon_code` (`code`),
    UNIQUE KEY `uk_coupon_template_user` (`template_id`, `user_id`),
    KEY `idx_user_coupon_user_status` (`user_id`, `status`),
    KEY `idx_user_coupon_order` (`order_id`),
    CONSTRAINT `fk_user_coupon_template` FOREIGN KEY (`template_id`) REFERENCES `coupon_template`(`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户优惠券';

-- ==================== 订单补券快照 ====================
-- discount_amount 已存在，改为「促销优惠 + 券优惠」总额；coupon_amount 单独记录券优惠用于统计。
ALTER TABLE `orders`
    ADD COLUMN `coupon_id` BIGINT DEFAULT NULL COMMENT '使用的优惠券 ID' AFTER `discount_amount`,
    ADD COLUMN `coupon_name` VARCHAR(128) DEFAULT NULL COMMENT '优惠券名称快照' AFTER `coupon_id`,
    ADD COLUMN `coupon_amount` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '优惠券优惠金额' AFTER `coupon_name`,
    ADD KEY `idx_orders_coupon` (`coupon_id`);
