-- ============================================================
-- 库存流水历史补录（生产执行）
-- 把「新库存模块上线前」的历史库存变动补进 stock_transaction
--
-- 背景：
--   1) 库存模块 2026-11-06 上线。上线前的订单走老逻辑，直接 UPDATE products.stock，
--      不写流水；上线后所有出入库统一走 product-service /internal/stock-write，
--      由 StockWriteService 写 stock_transaction。
--   2) 生产 stock_transaction 为空是正常的 —— 模块刚上线，生产还没产生过新订单。
--      但历史 126 单销售出库 + 2 单退货归还确实动过库存，流水里查不到，库存看板对不上账。
--   3) stock_order / stock_order_item 不用补：这两张表是「仓库作业单据」
--      （采购入库 / 手工出库 / 盘点调整），销售出库与退货归还都不产生单据，
--      生产没有任何仓库作业记录，空是设计如此。
--
-- 补录口径：
--   - 销售出库：orders 中 order_status ∈ (3 待发货, 4 已发货, 5 已完成) 的订单项 → OUT
--   - 退货归还：after_sales 中 status='refunded' 且 after_sale_type='return_refund' 的明细 → IN
--   - 不补：已取消订单（status=6，下单扣了又取消回补，净变化为 0，补进去会虚增出入库量）
--   - 不补：只退款（only_refund，不涉及库存）、换货（exchange，收回再发出，净变化为 0）
--
-- balance_after 口径：
--   历史种子库存起点已不可考，逐笔结存快照算不出来。
--   这里统一填「该 SKU 当前的结存」，remark 里写明是历史补录。
--   审计价值：能看出「哪些货出过、什么时候出的」；不能用于逐笔对账。
--
-- 幂等：NOT EXISTS 保证重跑不会重复插入（按 order_item_id + type 去重）
--      不能只按 order_item_id 去重：部分退货的订单仍处于 3/4/5，
--      该订单项会先补 OUT，再补 IN 时会被同一条 OUT 挡住，退货流水就丢了
-- 执行环境：生产 MySQL 5.7（不要用窗口函数/CTE）
-- ============================================================

-- ------------------------------------------------------------
-- 第 1 步：执行前先备份生产库
--   cd /www/java && bash backup-prod-db.sh
-- ------------------------------------------------------------

-- ------------------------------------------------------------
-- 第 2 步：预览（先跑这个，确认数量再往下走）
-- ------------------------------------------------------------
SELECT '销售出库（待插入行数）' AS item, COUNT(*) AS cnt
  FROM order_items oi
  JOIN orders o ON o.id = oi.order_id
 WHERE o.is_deleted = 0 AND o.order_status IN (3,4,5) AND oi.sku_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM stock_transaction tx WHERE tx.order_item_id = oi.id AND tx.type = 'OUT')
UNION ALL
SELECT '退货归还（待插入行数）', COUNT(*)
  FROM after_sale_items asi
  JOIN after_sales a ON a.id = asi.after_sale_id
  JOIN order_items oi ON oi.id = asi.order_item_id
 WHERE a.is_deleted = 0 AND asi.is_deleted = 0
   AND a.status = 'refunded' AND a.after_sale_type = 'return_refund'
   AND oi.sku_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM stock_transaction tx WHERE tx.order_item_id = oi.id AND tx.type = 'IN');

-- ------------------------------------------------------------
-- 第 3 步：补录销售出库流水
-- ------------------------------------------------------------
INSERT INTO stock_transaction
  (sku_id, sku_code, sku_name, spec_text, order_item_id, stock_order_id,
   type, reason, qty, balance_after, operator_id, operator_name, remark, create_time)
SELECT
  oi.sku_id,
  COALESCE(oi.sku_code, ps.sku_code),
  oi.product_name,
  oi.spec_json,
  oi.id,
  NULL,
  'OUT',
  '销售出库',
  oi.quantity,
  COALESCE(ps.stock, 0),
  o.user_id,
  u.name,
  '历史补录：库存模块 2026-11-06 上线前，下单时按商品级扣减库存',
  o.created_at
FROM order_items oi
JOIN orders o        ON o.id = oi.order_id
LEFT JOIN product_sku ps ON ps.id = oi.sku_id
LEFT JOIN `user` u  ON u.id = o.user_id
WHERE o.is_deleted = 0
  AND o.order_status IN (3,4,5)
  AND oi.sku_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM stock_transaction tx WHERE tx.order_item_id = oi.id AND tx.type = 'OUT');

-- ------------------------------------------------------------
-- 第 4 步：补录退货归还流水
-- ------------------------------------------------------------
INSERT INTO stock_transaction
  (sku_id, sku_code, sku_name, spec_text, order_item_id, stock_order_id,
   type, reason, qty, balance_after, operator_id, operator_name, remark, create_time)
SELECT
  oi.sku_id,
  COALESCE(oi.sku_code, ps.sku_code),
  asi.product_name,
  oi.spec_json,
  oi.id,
  NULL,
  'IN',
  '退货归还',
  asi.quantity,
  COALESCE(ps.stock, 0),
  o.user_id,
  u.name,
  '历史补录：库存模块 2026-11-06 上线前，退货审核通过后按商品级回补库存',
  a.updated_at
FROM after_sale_items asi
JOIN after_sales a     ON a.id = asi.after_sale_id
JOIN order_items oi    ON oi.id = asi.order_item_id
JOIN orders o          ON o.id = oi.order_id
LEFT JOIN product_sku ps ON ps.id = oi.sku_id
LEFT JOIN `user` u     ON u.id = o.user_id
WHERE a.is_deleted = 0
  AND asi.is_deleted = 0
  AND a.status = 'refunded'
  AND a.after_sale_type = 'return_refund'
  AND oi.sku_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM stock_transaction tx WHERE tx.order_item_id = oi.id AND tx.type = 'IN');

-- ------------------------------------------------------------
-- 第 5 步：校验
-- ------------------------------------------------------------
SELECT type, reason, COUNT(*) AS 条数, SUM(qty) AS 件数
  FROM stock_transaction GROUP BY type, reason;

-- 应当看到：OUT/销售出库 145 条、IN/退货归还 2 条（生产口径）
SELECT '剩余未补录的历史订单项' AS item, COUNT(*) AS cnt
  FROM order_items oi
  JOIN orders o ON o.id = oi.order_id
 WHERE o.is_deleted = 0 AND o.order_status IN (3,4,5) AND oi.sku_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM stock_transaction tx WHERE tx.order_item_id = oi.id AND tx.type = 'OUT');

-- ------------------------------------------------------------
-- 回滚
-- ------------------------------------------------------------
-- DELETE FROM stock_transaction WHERE remark LIKE '历史补录：%';
