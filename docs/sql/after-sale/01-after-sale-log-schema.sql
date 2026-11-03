-- ============================================================================
-- 售后变更流水表（after_sale_log）
--
-- 用途：
--   记录售后单每一次状态变化：谁操作的、从哪个状态到哪个状态、做的什么操作、
--   给买家看的公开说明。后台与小程序的「进度时间线」都读这张表。
--
-- 执行方式：
--   mysql -u root -p --default-character-set=utf8mb4 e_commerce_db < docs/sql/after-sale/01-after-sale-log-schema.sql
--
-- 说明：
--   1. 幂等：CREATE TABLE IF NOT EXISTS，可重复执行。
--   2. content 与 merchant_remark 分两列：商家内部备注只进 merchant_remark，
--      买家接口不返回，避免内部备注泄露。
--   3. COLLATE utf8mb4_general_ci：生产是 MySQL 5.7。
--   4. created_at 由 Java 传参写入，不用 NOW() —— 服务器时区是 UTC。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `after_sale_log` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '售后变更日志 ID',
    `after_sale_id`   BIGINT      NOT NULL                COMMENT '售后单 ID（不加外键，软删不影响日志留存）',
    `user_id`         BIGINT      NOT NULL DEFAULT 0      COMMENT '操作人 ID（admin.id 或 user.id）',
    `user_type`       TINYINT     NOT NULL DEFAULT 1      COMMENT '操作人类型：1 买家 2 后台管理员',
    `before_status`   VARCHAR(64) DEFAULT NULL            COMMENT '变更前状态（新建时为空）',
    `after_status`    VARCHAR(64) NOT NULL                COMMENT '变更后状态',
    `operate_type`    TINYINT     NOT NULL                COMMENT '操作类型：见 AfterSaleOperateTypeEnum',
    `content`         VARCHAR(255) NOT NULL               COMMENT '公开说明（买家可见）',
    `merchant_remark` VARCHAR(512) DEFAULT NULL           COMMENT '商家内部备注（仅后台可见，买家接口不返回）',
    `created_at`      DATETIME    DEFAULT NULL            COMMENT '创建时间（Java 传参）',
    `updated_at`      DATETIME    DEFAULT NULL            COMMENT '更新时间（Java 传参）',
    `is_deleted`      TINYINT     NOT NULL DEFAULT 0      COMMENT '软删除：0 正常 1 删除',
    PRIMARY KEY (`id`),
    KEY `idx_after_sale_log_as_id` (`after_sale_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '售后变更流水日志表';
