-- ============================================================================
-- 历史数据订正 ①：only_refund + returning 的遗留售后单
--
-- 背景：
--   新状态机里 only_refund（仅退款）只有 reviewing 能出发，没有 returning。
--   历史数据里有 only_refund + returning 的单（本地 1 条、生产 1 条），
--   在后台一个按钮都不显示，会卡死。
--
-- 处理：
--   订正为 reviewing —— 数据语义回归正常，不改状态机代码、不留长期债务。
--   （而不是给 only_refund 加 returning → refunding 特判：特判会永久留在代码里，
--     前端也会多出一条「仅退款退货中」的诡异路径。）
--
-- 执行顺序：本地与生产执行同一份。生产执行前先 ./deploy/backup-prod-db.sh 备份。
--
-- 注意：服务器时区是 UTC，updated_at 不用 NOW()，用 +8h 换算成本地时间。
-- ============================================================================

UPDATE after_sales
SET status = 'reviewing',
    updated_at = DATE_ADD(NOW(), INTERVAL 8 HOUR)
WHERE after_sale_type = 'only_refund'
  AND status = 'returning'
  AND is_deleted = 0;
