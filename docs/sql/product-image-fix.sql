-- 商品图片路径修正（配合图片迁移，由你手动执行一次）
--
-- 背景：Node 端 seed 数据里，商品 id=9（苹果MacBook Pro）的 image 被写成了 UUID
--   /image/upload/530c8603-9b9d-4b5b-b3e7-31f6ecdb7ea9.webp，但磁盘实际没有这个 UUID 文件，
--   真实图片是数字命名的 uploads/9.png（已统一转为 webp，放在 upload/image/upload/9.webp）。
--   其余 31 个商品都是 /image/upload/N.webp 数字命名，这里把 id=9 改回数字命名，保持一致。
--
-- 执行方式：mysql -u root -p123456 e_commerce_db < docs/sql/product-image-fix.sql

UPDATE `products`
SET `image` = '/image/upload/9.webp'
WHERE `id` = 9 AND `shop_id` = 1;
