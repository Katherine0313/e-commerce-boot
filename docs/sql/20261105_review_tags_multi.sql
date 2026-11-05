-- 评价标签改为多值：一条评价最多 4 个标签
-- 只执行一次；本地执行后必须同步生产
-- 生产 MySQL 5.7.44 支持 JSON 类型与 JSON_CONTAINS / JSON_QUOTE

-- 1) 中性标签并入负向（定稿只有 8 个标签，不含中性）
UPDATE product_reviews SET tag = '有待改进' WHERE tag = '中规中矩';

-- 2) 新增多值字段（保留旧 tag 列，避免旧版本服务读写报错）
ALTER TABLE product_reviews
  ADD COLUMN tags JSON NULL COMMENT '评价标签数组，最多 4 个' AFTER tag;

-- 3) 历史单值转数组
UPDATE product_reviews
SET tags = JSON_ARRAY(tag)
WHERE tag IS NOT NULL AND tag <> '';

-- 4) 旧字段停用（列保留，代码不再读写）
UPDATE product_reviews SET tag = NULL;
