-- 删除旧的 51 开头种子商品（旧版有伪变体重复）；先清购物车/库存引用
SET NAMES utf8mb4;
DELETE FROM cart WHERE product_id BETWEEN 51 AND 300;
DELETE FROM stock_alert WHERE product_id BETWEEN 51 AND 300;
DELETE FROM products WHERE id BETWEEN 51 AND 300 AND product_code LIKE 'SEED-%';
