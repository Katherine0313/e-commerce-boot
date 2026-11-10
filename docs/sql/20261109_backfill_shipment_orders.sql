-- ============================================================
-- 历史「销售出库单」回填（生产执行，一次性）
--
-- 背景：
--   2026-11-09 起 order-service 发货时会自动让 product-service 生成一张
--   type=SHIPMENT / biz_type=ORDER_SHIP 的出库单（只落单据，不重复扣库存）。
--   在该改动上线之前已经发货的历史订单没有出库单，这里一次性补出来，
--   让「出库单」列表能追溯历史发货。
--
-- 口径：
--   - 取 orders 中 order_status ∈ (4 已发货, 5 已完成, 7 已退款) 的订单
--     （7 是「发过货、随后整单退款」的终态，出库动作确实发生过，故一并补）
--   - 明细取 order_items 中 sku_id 非空、未删除的行
--   - 只写 stock_order / stock_order_item，不动 stock_transaction / product_sku.stock
--     （库存在下单时已扣减，流水也已由 20261107 脚本补录，重复补会虚增库存）
--   - create_time 取订单发货时间 ship_at（缺失时退回 updated_at / created_at）
--   - 单据号：CKBF + 发货日期 + 订单 id，唯一且不会和 OrderNoGenerator 的
--     CKyyyyMMddNNNN 冲突（生成器按 LIKE 'CKyyyyMMdd%' 取号，CKBF 开头不匹配）
--
-- 幂等：按 related_no + type='SHIPMENT' NOT EXISTS 去重，可重复执行
--      明细再按 order_id NOT EXISTS 兜底
-- 排序规则：stock_order 是后建的表（utf8mb4_0900_ai_ci / 各环境可能不同），
--      orders 是 utf8mb4_general_ci，关联时显式 COLLATE 到通用排序规则，避免
--      「Illegal mix of collations」；utf8mb4_general_ci 在 MySQL 5.7 / 8.0 都存在
-- 执行环境：生产 MySQL 5.7（不使用窗口函数 / CTE）
-- ============================================================

-- ------------------------------------------------------------
-- 第 1 步：执行前备份生产库
--   cd /www/java && bash backup-prod-db.sh
-- ------------------------------------------------------------

-- ------------------------------------------------------------
-- 第 2 步：预览（先确认数量再往下走）
-- ------------------------------------------------------------
SELECT '待补出库单（订单数）' AS item, COUNT(*) AS cnt
  FROM orders o
 WHERE o.is_deleted = 0
   AND o.order_status IN (4, 5, 7)
   AND EXISTS (SELECT 1 FROM order_items oi
                WHERE oi.order_id = o.id AND oi.is_deleted = 0 AND oi.sku_id IS NOT NULL)
   AND NOT EXISTS (SELECT 1 FROM stock_order so
                    WHERE so.related_no = o.order_no COLLATE utf8mb4_general_ci AND so.type = 'SHIPMENT')
UNION ALL
SELECT '待补出库单明细（行数）', COUNT(*)
  FROM order_items oi
  JOIN orders o ON o.id = oi.order_id
 WHERE o.is_deleted = 0
   AND o.order_status IN (4, 5, 7)
   AND oi.is_deleted = 0
   AND oi.sku_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM stock_order so
                    WHERE so.related_no = o.order_no COLLATE utf8mb4_general_ci AND so.type = 'SHIPMENT');

-- ------------------------------------------------------------
-- 第 3 步：补出库单主表
-- ------------------------------------------------------------
INSERT INTO stock_order
  (order_no, type, biz_type, related_no, status, total_qty, total_amount,
   remark, operator_id, operator_name, create_time, update_time)
SELECT
  CONCAT('CKBF',
         DATE_FORMAT(COALESCE(o.ship_at, o.updated_at, o.created_at, NOW()), '%Y%m%d'),
         o.id),
  'SHIPMENT',
  'ORDER_SHIP',
  o.order_no,
  1,
  (SELECT COALESCE(SUM(oi2.quantity), 0)
     FROM order_items oi2
    WHERE oi2.order_id = o.id AND oi2.is_deleted = 0 AND oi2.sku_id IS NOT NULL),
  NULL,
  '历史数据回填：订单发货自动生成出库单（不重复扣库存）',
  NULL,
  '系统回填',
  COALESCE(o.ship_at, o.updated_at, o.created_at, NOW()),
  COALESCE(o.ship_at, o.updated_at, o.created_at, NOW())
FROM orders o
WHERE o.is_deleted = 0
  AND o.order_status IN (4, 5, 7)
  AND EXISTS (SELECT 1 FROM order_items oi
               WHERE oi.order_id = o.id AND oi.is_deleted = 0 AND oi.sku_id IS NOT NULL)
  AND NOT EXISTS (SELECT 1 FROM stock_order so
                   WHERE so.related_no = o.order_no COLLATE utf8mb4_general_ci AND so.type = 'SHIPMENT');

-- ------------------------------------------------------------
-- 第 4 步：补出库单明细
-- ------------------------------------------------------------
INSERT INTO stock_order_item
  (order_id, sku_id, sku_code, sku_name, spec_text, qty, cost_price)
SELECT
  so.id,
  oi.sku_id,
  COALESCE(oi.sku_code, ps.sku_code),
  oi.product_name,
  COALESCE(ps.spec_key, oi.spec_json),
  oi.quantity,
  ps.cost_price
FROM order_items oi
JOIN orders o            ON o.id = oi.order_id
JOIN stock_order so      ON so.related_no = o.order_no COLLATE utf8mb4_general_ci AND so.type = 'SHIPMENT'
LEFT JOIN product_sku ps ON ps.id = oi.sku_id
WHERE o.is_deleted = 0
  AND o.order_status IN (4, 5, 7)
  AND oi.is_deleted = 0
  AND oi.sku_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM stock_order_item soi WHERE soi.order_id = so.id);

-- ------------------------------------------------------------
-- 第 5 步：校验
-- ------------------------------------------------------------
SELECT '回填出库单数' AS item, COUNT(*) AS cnt
  FROM stock_order WHERE type = 'SHIPMENT' AND order_no LIKE 'CKBF%'
UNION ALL
SELECT '回填出库单明细数', COUNT(*)
  FROM stock_order_item soi
  JOIN stock_order so ON so.id = soi.order_id
 WHERE so.type = 'SHIPMENT' AND so.order_no LIKE 'CKBF%';

-- 应看到：主表条数 = 预览中的订单数；明细条数 = 预览中的明细行数
SELECT '回填后仍缺出库单的已发货订单' AS item, COUNT(*) AS cnt
  FROM orders o
 WHERE o.is_deleted = 0
   AND o.order_status IN (4, 5, 7)
   AND EXISTS (SELECT 1 FROM order_items oi
                WHERE oi.order_id = o.id AND oi.is_deleted = 0 AND oi.sku_id IS NOT NULL)
   AND NOT EXISTS (SELECT 1 FROM stock_order so
                    WHERE so.related_no = o.order_no COLLATE utf8mb4_general_ci AND so.type = 'SHIPMENT');

-- 抽查几张：单据号 / 关联订单号 / 明细行数 / 总数量
SELECT so.order_no, so.related_no, so.total_qty,
       (SELECT COUNT(1) FROM stock_order_item i WHERE i.order_id = so.id) AS item_count,
       so.create_time
  FROM stock_order so
 WHERE so.type = 'SHIPMENT' AND so.order_no LIKE 'CKBF%'
 ORDER BY so.id DESC LIMIT 10;

-- ------------------------------------------------------------
-- 回滚（只删回填数据，不影响人工建的单据）
-- ------------------------------------------------------------
-- DELETE soi FROM stock_order_item soi
--   JOIN stock_order so ON so.id = soi.order_id
--  WHERE so.type = 'SHIPMENT' AND so.order_no LIKE 'CKBF%';
-- DELETE FROM stock_order WHERE type = 'SHIPMENT' AND order_no LIKE 'CKBF%';
