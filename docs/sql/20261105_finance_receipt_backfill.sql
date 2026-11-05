-- 历史已支付订单补收款单（幂等，可重复执行）
-- 财务模块上线前已支付的订单不会经过 payOrder，这里按「已支付且无单据」批量补一张待审核单据，
-- 保证「每一笔线上支付都有一张收款单」这条不变量从第一天起成立。
-- 说明：单号用 FIN + 收款时间 + 订单 ID（补数据专用格式，不会与线上支付生成的单号重复）。

INSERT INTO finance_receipt (receipt_no, order_id, order_no, user_id, channel, account_id,
                             amount, paid_at, status, is_deleted, create_time, update_time)
SELECT CONCAT('FIN', DATE_FORMAT(o.pay_at, '%Y%m%d%H%i%s'), LPAD(o.id, 8, '0')) AS receipt_no,
       o.id,
       o.order_no,
       o.user_id,
       CASE UPPER(o.pay_type)
           WHEN 'WECHAT' THEN 'WeChat'
           WHEN 'ALIPAY' THEN 'Alipay'
           WHEN 'BANKCARD' THEN 'BankCard'
           ELSE o.pay_type
           END AS channel,
       (SELECT a.id
        FROM finance_account a
        WHERE a.channel = CASE UPPER(o.pay_type)
                              WHEN 'WECHAT' THEN 'WeChat'
                              WHEN 'ALIPAY' THEN 'Alipay'
                              WHEN 'BANKCARD' THEN 'BankCard'
                              ELSE o.pay_type
                              END
          AND a.is_deleted = 0
          AND a.status = 1
        ORDER BY a.is_default DESC, a.id ASC
        LIMIT 1) AS account_id,
       o.pay_amount,
       o.pay_at,
       0,
       0,
       NOW(),
       NOW()
FROM orders o
LEFT JOIN finance_receipt r ON r.order_id = o.id
WHERE o.order_status IN (3, 4, 5, 7)
  AND o.pay_at IS NOT NULL
  AND o.pay_type IS NOT NULL
  AND o.pay_type <> ''
  AND r.id IS NULL;
