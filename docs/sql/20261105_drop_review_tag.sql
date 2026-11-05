-- 删除 product_reviews 的旧单值标签列 tag。
-- 前提：多值标签已全量迁移到 tags（见 20261105_review_tags_multi.sql），且新代码已不再读写 tag。
-- 注意：先发布新版 order-service，再执行本脚本，避免旧代码仍在查询 tag 列时报 Unknown column。
ALTER TABLE product_reviews DROP COLUMN tag;
