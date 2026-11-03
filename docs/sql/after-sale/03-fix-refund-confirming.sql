-- ============================================================================
-- 历史数据订正 ②：refund_confirming 状态（本地 1 条、生产 2 条，均 only_refund）
--
-- 背景：
--   项目不接真实支付，没有「款已发起、等款到账」这个真实时刻，
--   refund_confirming 状态是多余的（「确认退款」是一个操作，不是一个状态）。
--   状态机改造后这个状态被删除，遗留单必须一起订正，否则查不到状态名。
--
-- 处理：
--   订正为 refunded；refunded_at 取原 refund_at（当初「确认退款」的时刻），
--   与订单取消自动退款 refund_at = refunded_at 的口径一致。
--
-- 执行顺序：本地与生产执行同一份。生产执行前先 ./deploy/backup-prod-db.sh 备份。
--
-- ⚠️ 子表更新必须在主表更新之前：
--    主表状态一旦改成 refunded，下面子表语句就定位不到这批单了。
--
-- 注意：服务器时区是 UTC，updated_at 不用 NOW()，用 +8h 换算成本地时间。
-- ============================================================================

-- 步骤 1：先补子表 refunded_at（此时主表状态还是 refund_confirming，才能定位到）
UPDATE after_sale_only_refunds o
JOIN after_sales a ON a.id = o.after_sale_id AND a.is_deleted = 0
SET o.refunded_at = COALESCE(o.refund_at, a.updated_at)
WHERE a.status = 'refund_confirming'
  AND a.after_sale_type = 'only_refund'
  AND o.is_deleted = 0
  AND o.refunded_at IS NULL;

UPDATE after_sale_returns o
JOIN after_sales a ON a.id = o.after_sale_id AND a.is_deleted = 0
SET o.refunded_at = COALESCE(o.refund_at, a.updated_at)
WHERE a.status = 'refund_confirming'
  AND a.after_sale_type = 'return_refund'
  AND o.is_deleted = 0
  AND o.refunded_at IS NULL;

-- 步骤 2：主表状态订正
UPDATE after_sales
SET status = 'refunded',
    updated_at = DATE_ADD(NOW(), INTERVAL 8 HOUR)
WHERE status = 'refund_confirming'
  AND is_deleted = 0;
