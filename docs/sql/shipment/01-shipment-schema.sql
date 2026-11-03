-- ============================================================================
-- 发货管理模块：3 张表
--   1. delivery_express     快递公司字典
--   2. order_delivery       订单包裹发货表
--   3. order_delivery_log   发货操作流水表
--
-- 执行方式（本地库与生产库执行同一份）：
--   mysql -u root -p --default-character-set=utf8mb4 e_commerce_db < docs/sql/shipment/01-shipment-schema.sql
--
-- 说明：
--   1. 幂等：CREATE TABLE IF NOT EXISTS，可重复执行。
--   2. COLLATE utf8mb4_general_ci：生产是 MySQL 5.7。
--   3. 时间戳全部由 Java 传参写入，不用 NOW() —— 服务器时区是 UTC。
--   4. order_delivery 关联 orders(id) 级联删除；order_delivery_log 不加外键
--      （照抄 after_sale_log 的做法，软删订单不影响流水留存）。
-- ============================================================================

-- ==================== 快递公司字典 ====================
CREATE TABLE IF NOT EXISTS `delivery_express` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `code`        VARCHAR(64)  NOT NULL                COMMENT '快递公司编码（如 shunfeng）',
    `name`        VARCHAR(64)  NOT NULL                COMMENT '快递公司名称',
    `logo`        VARCHAR(256) DEFAULT NULL           COMMENT 'logo 相对路径，如 /image/express/xxx.webp',
    `sort_order`  INT          NOT NULL DEFAULT 0      COMMENT '排序（小的在前）',
    `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 禁用',
    `remark`      VARCHAR(255) DEFAULT NULL           COMMENT '备注',
    `is_deleted`  TINYINT      NOT NULL DEFAULT 0      COMMENT '软删除：1 已删除 / 0 正常',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_delivery_express_code` (`code`),
    KEY `idx_delivery_express_status_sort` (`status`, `sort_order`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '快递公司字典表';

-- ==================== 订单包裹发货表 ====================
CREATE TABLE IF NOT EXISTS `order_delivery` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `order_id`        BIGINT       NOT NULL                COMMENT '订单 ID，关联 orders.id',
    `order_no`        VARCHAR(64)  NOT NULL                COMMENT '订单编号（快照）',
    `package_no`      VARCHAR(64)  NOT NULL                COMMENT '包裹编号（PKG+时间戳+随机）',
    `express_code`    VARCHAR(64)  NOT NULL                COMMENT '快递公司编码，关联 delivery_express.code',
    `express_name`    VARCHAR(64)  NOT NULL                COMMENT '快递公司名称快照',
    `tracking_number` VARCHAR(64)  NOT NULL                COMMENT '快递单号',
    `delivery_type`   TINYINT      NOT NULL DEFAULT 1      COMMENT '发货类型：1 正常发货 2 补发',
    `delivery_time`   DATETIME     NOT NULL                COMMENT '发货时间（Java 传参）',
    `remark`          VARCHAR(512) DEFAULT NULL           COMMENT '发货备注',
    `is_deleted`      TINYINT      NOT NULL DEFAULT 0      COMMENT '软删除：1 已删除 / 0 正常',
    `created_at`      DATETIME     NOT NULL                COMMENT '创建时间（Java 传参）',
    `updated_at`      DATETIME     DEFAULT NULL            COMMENT '更新时间（Java 传参）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_delivery_package_no` (`package_no`),
    KEY `idx_order_delivery_order_id` (`order_id`),
    KEY `idx_order_delivery_tracking_number` (`tracking_number`),
    KEY `idx_order_delivery_created` (`created_at`),
    CONSTRAINT `fk_order_delivery_order` FOREIGN KEY (`order_id`) REFERENCES `orders`(`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '订单包裹发货表';

-- ==================== 发货操作流水表 ====================
CREATE TABLE IF NOT EXISTS `order_delivery_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '流水 ID',
    `delivery_id`   BIGINT       NOT NULL                COMMENT '包裹 ID（不加外键，同 after_sale_log）',
    `package_no`    VARCHAR(64)  NOT NULL                COMMENT '包裹编号（快照）',
    `order_id`      BIGINT       NOT NULL                COMMENT '订单 ID（快照）',
    `order_no`      VARCHAR(64)  NOT NULL                COMMENT '订单编号（快照）',
    `operate_type`  TINYINT      NOT NULL                COMMENT '操作类型：1 创建发货 2 补发 3 修改快递信息',
    `content`       VARCHAR(255) NOT NULL                COMMENT '操作说明，如：发货成功：顺丰 SF123456',
    `remark`        VARCHAR(512) DEFAULT NULL           COMMENT '内部备注（仅后台可见）',
    `operator_id`   BIGINT       NOT NULL DEFAULT 0      COMMENT '操作人 ID（后台管理员）',
    `operator_type` TINYINT      NOT NULL DEFAULT 2      COMMENT '操作人类型：1 系统 2 后台管理员',
    `created_at`    DATETIME     NOT NULL                COMMENT '创建时间（Java 传参）',
    `updated_at`    DATETIME     DEFAULT NULL            COMMENT '更新时间（Java 传参）',
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0      COMMENT '软删除：1 已删除 / 0 正常',
    PRIMARY KEY (`id`),
    KEY `idx_order_delivery_log_delivery_id` (`delivery_id`),
    KEY `idx_order_delivery_log_order_id` (`order_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '发货操作流水表';
