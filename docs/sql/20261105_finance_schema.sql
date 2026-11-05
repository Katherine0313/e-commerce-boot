-- 财务模块建表：收款账户 + 收款单
-- 本地执行后必须同步生产库（项目硬规则）

CREATE TABLE IF NOT EXISTS `finance_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `account_name` varchar(64) NOT NULL COMMENT '账户名称，如 微信商户-主店',
  `channel` varchar(20) NOT NULL COMMENT '渠道：WeChat / Alipay / BankCard（与 orders.pay_type 同字典）',
  `merchant_no` varchar(64) NOT NULL COMMENT '商户号（对账展示用，不存密钥）',
  `is_default` tinyint NOT NULL DEFAULT '0' COMMENT '同渠道默认：1 是 / 0 否',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 启用 / 0 停用',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除：1 已删除 / 0 正常',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_merchant` (`channel`, `merchant_no`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='收款账户';

CREATE TABLE IF NOT EXISTS `finance_receipt` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `receipt_no` varchar(40) NOT NULL COMMENT '收款单号：FIN + yyyyMMddHHmmssSSS + 4 位随机',
  `order_id` bigint NOT NULL COMMENT 'orders.id，唯一索引防重复生成',
  `order_no` varchar(40) NOT NULL COMMENT '订单号快照',
  `user_id` bigint NOT NULL COMMENT '买家 user.id',
  `channel` varchar(20) NOT NULL COMMENT '支付渠道，来自 orders.pay_type',
  `account_id` bigint DEFAULT NULL COMMENT '收款账户 ID；渠道未配置账户时为空',
  `amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '收款金额，等于 orders.pay_amount',
  `paid_at` datetime NOT NULL COMMENT '收款时间，等于 orders.pay_at',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0 待审核 / 1 已审核',
  `audit_by` bigint DEFAULT NULL COMMENT '审核人 admin.id',
  `audit_name` varchar(64) DEFAULT NULL COMMENT '审核人账号快照',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '保留字段：1 已删除 / 0 正常',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_receipt_no` (`receipt_no`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_status_paid_at` (`status`, `paid_at`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_channel` (`channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='收款单';
