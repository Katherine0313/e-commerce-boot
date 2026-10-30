-- 会员模块（user-service）：会员等级 + 用户会员字段 + 优惠券等级门槛
-- 执行方式：
--   mysql -u root -p --default-character-set=utf8mb4 e_commerce_db < docs/sql/member/01-member-schema.sql
--
-- 说明：
--   1. 只建表 / 加列 / 插 4 条等级种子，幂等可重复执行；不删除、不清空业务数据。
--   2. 等级门槛默认 0 / 500 / 2000 / 5000，可在后台修改。
--   3. 成长值 = 已支付订单实付 - 已退款金额（1 元 = 1 成长值），由 user-service 刷新时回写。
--   4. 兼容 MySQL 5.7：显式 COLLATE utf8mb4_general_ci。

-- ==================== 会员等级 ====================
CREATE TABLE IF NOT EXISTS `member_level` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '等级 ID',
    `name`             VARCHAR(32)  NOT NULL                COMMENT '等级名称',
    `growth_threshold` INT          NOT NULL DEFAULT 0      COMMENT '成长值门槛',
    `benefits`         VARCHAR(255) DEFAULT NULL            COMMENT '权益文案（C 端展示）',
    `sort_order`       INT          NOT NULL DEFAULT 0      COMMENT '排序值，越小越靠前',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     DEFAULT NULL            COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_member_level_threshold` (`growth_threshold`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '会员等级';

INSERT IGNORE INTO `member_level` (`id`, `name`, `growth_threshold`, `benefits`, `sort_order`) VALUES
    (1, '普通会员', 0, '注册即享会员权益', 1),
    (2, '黄金会员', 500, '会员专享券，专属活动优先购', 2),
    (3, '白金会员', 2000, '更高额度会员券，专属客服', 3),
    (4, '钻石会员', 5000, '最高等级会员券，新品优先购', 4);

-- ==================== 用户表补会员字段 ====================
ALTER TABLE `user`
    ADD COLUMN `member_level_id` BIGINT NOT NULL DEFAULT 1 COMMENT '会员等级 ID' AFTER `avatar`,
    ADD COLUMN `growth_value` INT NOT NULL DEFAULT 0 COMMENT '成长值' AFTER `member_level_id`,
    ADD COLUMN `total_paid_amount` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '累计实付（已扣退款）' AFTER `growth_value`,
    ADD COLUMN `paid_order_count` INT NOT NULL DEFAULT 0 COMMENT '已支付订单数' AFTER `total_paid_amount`,
    ADD KEY `idx_user_member_level` (`member_level_id`);

-- ==================== 优惠券增加会员等级门槛 ====================
ALTER TABLE `coupon_template`
    ADD COLUMN `min_member_level` BIGINT NOT NULL DEFAULT 0 COMMENT '最低会员等级 ID，0=不限' AFTER `target_group`;
