-- 收款账户初始化：微信 / 支付宝 / 银行卡各一条默认账户
-- 商户号只是演示值，上线后可在「财务管理 → 收款账户」里改
-- 本地执行后必须同步生产库（项目硬规则）

INSERT INTO finance_account (account_name, channel, merchant_no, is_default, status, remark, is_deleted, create_time, update_time)
VALUES
  ('微信商户-主店',   'WeChat',   'WX2026000001', 1, 1, '演示账户，可在收款账户页修改', 0, NOW(), NOW()),
  ('支付宝商户-主店', 'Alipay',   'ALI2026000001', 1, 1, '演示账户，可在收款账户页修改', 0, NOW(), NOW()),
  ('银行卡收款账户',  'BankCard', 'BANK2026000001', 1, 1, '演示账户，可在收款账户页修改', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  account_name = VALUES(account_name),
  is_default = VALUES(is_default),
  status = VALUES(status),
  update_time = NOW();
