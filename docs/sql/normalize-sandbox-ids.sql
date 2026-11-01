-- ============================================================================
-- 压平「沙箱编号段」主键：把 8 位长 id（100000xx / 200000xx / ... / 800000xx）
-- 改回正常长度的连续 id，并级联修正所有引用列。
--
-- 背景：
--   早期为隔离沙箱环境，各表主键按「段位编号」手工指定（订单 51000220、
--   用户 29000022、售后 10009115……）。沙箱已下线，这些号段没跟着回收，
--   结果新插入的数据 id 继续往上跳（本地 orders 下个 id 是 51000221，
--   生产是 249000290），既难看又会跟旧号段撞车。
--
-- 处理原则（重要）：
--   1. **只动 id >= 100000 的行**，本来就是正常 id 的行一个都不改
--      —— 老订单链接、老用户 id、老商品 id 全部保持可用。
--   2. 新 id = 该表「现有正常 id 最大值 + 排名」，按老 id 升序分配，
--      保证新老 id 都单调递增，且与创建顺序基本一致。
--   3. 引用列用映射表做 JOIN 一次性批量改，**不依赖执行顺序**：
--      映射表保存了 old→new 全量快照，主键改完后引用列依然能匹配上。
--   4. 脚本自通用：映射靠相关子查询算，不写死行数/具体 id，
--      本地与生产数据量不同，各跑各的都能算对。
--   5. 唯一约束只有 username/openid/order_no，不涉及 id，不受影响。
--
-- 影响：
--   - 老 id 失效的只有沙箱段那批行（演示数据为主）；
--     已分享出去的旧订单链接、订阅消息里的旧 id 会失效。
--   - C 端 JWT 里带 user id，改 id 后旧 token 需重新静默登录（自动完成）；
--     后台 admin id=1 不变，仅 3~4 个演示账号的 id 变化、需重新登录。
--
-- 执行方式：
--   mysql -u root -p --default-character-set=utf8mb4 e_commerce_db < docs/sql/normalize-sandbox-ids.sql
--   或 ./deploy/run-sql-prod.sh /www/java/sql/normalize-sandbox-ids.sql
--
-- ⚠️ 生产执行前务必先备份：bash deploy/backup-prod-db.sh
-- ============================================================================

SET SESSION foreign_key_checks = 0;

-- ==================== 1. user ====================
DROP TEMPORARY TABLE IF EXISTS tmp_map;
CREATE TEMPORARY TABLE tmp_map (old_id BIGINT PRIMARY KEY, new_id BIGINT NOT NULL);
INSERT INTO tmp_map (old_id, new_id)
SELECT t.id,
       (SELECT COALESCE(MAX(x.id), 0) FROM `user` x WHERE x.id < 100000)
       + (SELECT COUNT(*) FROM `user` y WHERE y.id >= 100000 AND y.id < t.id) + 1
FROM `user` t WHERE t.id >= 100000;
UPDATE orders        o JOIN tmp_map m ON o.user_id     = m.old_id SET o.user_id     = m.new_id;
UPDATE address       a JOIN tmp_map m ON a.user_id     = m.old_id SET a.user_id     = m.new_id;
UPDATE cart          c JOIN tmp_map m ON c.user_id     = m.old_id SET c.user_id     = m.new_id;
UPDATE feedback      f JOIN tmp_map m ON f.user_id     = m.old_id SET f.user_id     = m.new_id;
UPDATE product_favorites pf JOIN tmp_map m ON pf.user_id = m.old_id SET pf.user_id = m.new_id;
UPDATE product_reviews   pr JOIN tmp_map m ON pr.user_id = m.old_id SET pr.user_id = m.new_id;
UPDATE promotion_usage  pu JOIN tmp_map m ON pu.user_id = m.old_id SET pu.user_id = m.new_id;
UPDATE user_coupon      uc JOIN tmp_map m ON uc.user_id = m.old_id SET uc.user_id = m.new_id;
UPDATE `user` u JOIN tmp_map m ON u.id = m.old_id SET u.id = m.new_id;
DROP TEMPORARY TABLE IF EXISTS tmp_map;

-- ==================== 2. admin ====================
-- 注意：admin.id = 1 是超级管理员（代码里按 id=1 特判），不动。
DROP TEMPORARY TABLE IF EXISTS tmp_map;
CREATE TEMPORARY TABLE tmp_map (old_id BIGINT PRIMARY KEY, new_id BIGINT NOT NULL);
INSERT INTO tmp_map (old_id, new_id)
SELECT t.id,
       (SELECT COALESCE(MAX(x.id), 0) FROM admin x WHERE x.id < 100000)
       + (SELECT COUNT(*) FROM admin y WHERE y.id >= 100000 AND y.id < t.id) + 1
FROM admin t WHERE t.id >= 100000;
UPDATE admin_user_role ur JOIN tmp_map m ON ur.admin_id = m.old_id SET ur.admin_id = m.new_id;
UPDATE admin a JOIN tmp_map m ON a.id = m.old_id SET a.id = m.new_id;
DROP TEMPORARY TABLE IF EXISTS tmp_map;

-- ==================== 3. tags ====================
DROP TEMPORARY TABLE IF EXISTS tmp_map;
CREATE TEMPORARY TABLE tmp_map (old_id BIGINT PRIMARY KEY, new_id BIGINT NOT NULL);
INSERT INTO tmp_map (old_id, new_id)
SELECT t.id,
       (SELECT COALESCE(MAX(x.id), 0) FROM tags x WHERE x.id < 100000)
       + (SELECT COUNT(*) FROM tags y WHERE y.id >= 100000 AND y.id < t.id) + 1
FROM tags t WHERE t.id >= 100000;
UPDATE products p JOIN tmp_map m ON p.tag_id = m.old_id SET p.tag_id = m.new_id;
UPDATE tags t JOIN tmp_map m ON t.id = m.old_id SET t.id = m.new_id;
DROP TEMPORARY TABLE IF EXISTS tmp_map;

-- ==================== 4. address ====================
DROP TEMPORARY TABLE IF EXISTS tmp_map;
CREATE TEMPORARY TABLE tmp_map (old_id BIGINT PRIMARY KEY, new_id BIGINT NOT NULL);
INSERT INTO tmp_map (old_id, new_id)
SELECT t.id,
       (SELECT COALESCE(MAX(x.id), 0) FROM address x WHERE x.id < 100000)
       + (SELECT COUNT(*) FROM address y WHERE y.id >= 100000 AND y.id < t.id) + 1
FROM address t WHERE t.id >= 100000;
UPDATE address a JOIN tmp_map m ON a.id = m.old_id SET a.id = m.new_id;
DROP TEMPORARY TABLE IF EXISTS tmp_map;

-- ==================== 5. orders ====================
DROP TEMPORARY TABLE IF EXISTS tmp_map;
CREATE TEMPORARY TABLE tmp_map (old_id BIGINT PRIMARY KEY, new_id BIGINT NOT NULL);
INSERT INTO tmp_map (old_id, new_id)
SELECT t.id,
       (SELECT COALESCE(MAX(x.id), 0) FROM orders x WHERE x.id < 100000)
       + (SELECT COUNT(*) FROM orders y WHERE y.id >= 100000 AND y.id < t.id) + 1
FROM orders t WHERE t.id >= 100000;
UPDATE order_items     oi JOIN tmp_map m ON oi.order_id    = m.old_id SET oi.order_id    = m.new_id;
UPDATE after_sales     a  JOIN tmp_map m ON a.order_id     = m.old_id SET a.order_id     = m.new_id;
UPDATE product_reviews pr JOIN tmp_map m ON pr.order_id    = m.old_id SET pr.order_id    = m.new_id;
UPDATE user_coupon     uc JOIN tmp_map m ON uc.order_id    = m.old_id SET uc.order_id    = m.new_id;
UPDATE promotion_usage pu JOIN tmp_map m ON pu.order_id    = m.old_id SET pu.order_id    = m.new_id;
UPDATE orders o JOIN tmp_map m ON o.id = m.old_id SET o.id = m.new_id;
DROP TEMPORARY TABLE IF EXISTS tmp_map;

-- ==================== 6. order_items ====================
DROP TEMPORARY TABLE IF EXISTS tmp_map;
CREATE TEMPORARY TABLE tmp_map (old_id BIGINT PRIMARY KEY, new_id BIGINT NOT NULL);
INSERT INTO tmp_map (old_id, new_id)
SELECT t.id,
       (SELECT COALESCE(MAX(x.id), 0) FROM order_items x WHERE x.id < 100000)
       + (SELECT COUNT(*) FROM order_items y WHERE y.id >= 100000 AND y.id < t.id) + 1
FROM order_items t WHERE t.id >= 100000;
UPDATE after_sale_items ai JOIN tmp_map m ON ai.order_item_id = m.old_id SET ai.order_item_id = m.new_id;
UPDATE order_items oi JOIN tmp_map m ON oi.id = m.old_id SET oi.id = m.new_id;
DROP TEMPORARY TABLE IF EXISTS tmp_map;

-- ==================== 7. after_sales ====================
DROP TEMPORARY TABLE IF EXISTS tmp_map;
CREATE TEMPORARY TABLE tmp_map (old_id BIGINT PRIMARY KEY, new_id BIGINT NOT NULL);
INSERT INTO tmp_map (old_id, new_id)
SELECT t.id,
       (SELECT COALESCE(MAX(x.id), 0) FROM after_sales x WHERE x.id < 100000)
       + (SELECT COUNT(*) FROM after_sales y WHERE y.id >= 100000 AND y.id < t.id) + 1
FROM after_sales t WHERE t.id >= 100000;
UPDATE after_sale_items          ai JOIN tmp_map m ON ai.after_sale_id = m.old_id SET ai.after_sale_id = m.new_id;
UPDATE after_sale_only_refunds   ao JOIN tmp_map m ON ao.after_sale_id = m.old_id SET ao.after_sale_id = m.new_id;
UPDATE after_sale_returns        ar JOIN tmp_map m ON ar.after_sale_id = m.old_id SET ar.after_sale_id = m.new_id;
UPDATE after_sale_exchange_returns ae JOIN tmp_map m ON ae.after_sale_id = m.old_id SET ae.after_sale_id = m.new_id;
UPDATE after_sales a JOIN tmp_map m ON a.id = m.old_id SET a.id = m.new_id;
DROP TEMPORARY TABLE IF EXISTS tmp_map;

-- ==================== 8. after_sale_items ====================
DROP TEMPORARY TABLE IF EXISTS tmp_map;
CREATE TEMPORARY TABLE tmp_map (old_id BIGINT PRIMARY KEY, new_id BIGINT NOT NULL);
INSERT INTO tmp_map (old_id, new_id)
SELECT t.id,
       (SELECT COALESCE(MAX(x.id), 0) FROM after_sale_items x WHERE x.id < 100000)
       + (SELECT COUNT(*) FROM after_sale_items y WHERE y.id >= 100000 AND y.id < t.id) + 1
FROM after_sale_items t WHERE t.id >= 100000;
UPDATE after_sale_items a JOIN tmp_map m ON a.id = m.old_id SET a.id = m.new_id;
DROP TEMPORARY TABLE IF EXISTS tmp_map;

-- ==================== 9. after_sale_only_refunds ====================
DROP TEMPORARY TABLE IF EXISTS tmp_map;
CREATE TEMPORARY TABLE tmp_map (old_id BIGINT PRIMARY KEY, new_id BIGINT NOT NULL);
INSERT INTO tmp_map (old_id, new_id)
SELECT t.id,
       (SELECT COALESCE(MAX(x.id), 0) FROM after_sale_only_refunds x WHERE x.id < 100000)
       + (SELECT COUNT(*) FROM after_sale_only_refunds y WHERE y.id >= 100000 AND y.id < t.id) + 1
FROM after_sale_only_refunds t WHERE t.id >= 100000;
UPDATE after_sale_only_refunds a JOIN tmp_map m ON a.id = m.old_id SET a.id = m.new_id;
DROP TEMPORARY TABLE IF EXISTS tmp_map;

-- ==================== 10. after_sale_returns ====================
DROP TEMPORARY TABLE IF EXISTS tmp_map;
CREATE TEMPORARY TABLE tmp_map (old_id BIGINT PRIMARY KEY, new_id BIGINT NOT NULL);
INSERT INTO tmp_map (old_id, new_id)
SELECT t.id,
       (SELECT COALESCE(MAX(x.id), 0) FROM after_sale_returns x WHERE x.id < 100000)
       + (SELECT COUNT(*) FROM after_sale_returns y WHERE y.id >= 100000 AND y.id < t.id) + 1
FROM after_sale_returns t WHERE t.id >= 100000;
UPDATE after_sale_returns a JOIN tmp_map m ON a.id = m.old_id SET a.id = m.new_id;
DROP TEMPORARY TABLE IF EXISTS tmp_map;

-- ==================== 11. after_sale_exchange_returns ====================
DROP TEMPORARY TABLE IF EXISTS tmp_map;
CREATE TEMPORARY TABLE tmp_map (old_id BIGINT PRIMARY KEY, new_id BIGINT NOT NULL);
INSERT INTO tmp_map (old_id, new_id)
SELECT t.id,
       (SELECT COALESCE(MAX(x.id), 0) FROM after_sale_exchange_returns x WHERE x.id < 100000)
       + (SELECT COUNT(*) FROM after_sale_exchange_returns y WHERE y.id >= 100000 AND y.id < t.id) + 1
FROM after_sale_exchange_returns t WHERE t.id >= 100000;
UPDATE after_sale_exchange_returns a JOIN tmp_map m ON a.id = m.old_id SET a.id = m.new_id;
DROP TEMPORARY TABLE IF EXISTS tmp_map;

-- ==================== 12. cart ====================
DROP TEMPORARY TABLE IF EXISTS tmp_map;
CREATE TEMPORARY TABLE tmp_map (old_id BIGINT PRIMARY KEY, new_id BIGINT NOT NULL);
INSERT INTO tmp_map (old_id, new_id)
SELECT t.id,
       (SELECT COALESCE(MAX(x.id), 0) FROM cart x WHERE x.id < 100000)
       + (SELECT COUNT(*) FROM cart y WHERE y.id >= 100000 AND y.id < t.id) + 1
FROM cart t WHERE t.id >= 100000;
UPDATE cart c JOIN tmp_map m ON c.id = m.old_id SET c.id = m.new_id;
DROP TEMPORARY TABLE IF EXISTS tmp_map;

SET SESSION foreign_key_checks = 1;


-- ==================== 13. 重置 AUTO_INCREMENT 计数器 ====================
-- 只改计数器，不动任何数据行。
SET @sql := CONCAT('ALTER TABLE `user` AUTO_INCREMENT = ', (SELECT MAX(id) + 1 FROM `user`));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := CONCAT('ALTER TABLE `admin` AUTO_INCREMENT = ', (SELECT MAX(id) + 1 FROM admin));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := CONCAT('ALTER TABLE `tags` AUTO_INCREMENT = ', (SELECT MAX(id) + 1 FROM tags));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := CONCAT('ALTER TABLE `address` AUTO_INCREMENT = ', (SELECT MAX(id) + 1 FROM address));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := CONCAT('ALTER TABLE `orders` AUTO_INCREMENT = ', (SELECT MAX(id) + 1 FROM orders));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := CONCAT('ALTER TABLE `order_items` AUTO_INCREMENT = ', (SELECT MAX(id) + 1 FROM order_items));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := CONCAT('ALTER TABLE `after_sales` AUTO_INCREMENT = ', (SELECT MAX(id) + 1 FROM after_sales));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := CONCAT('ALTER TABLE `after_sale_items` AUTO_INCREMENT = ', (SELECT MAX(id) + 1 FROM after_sale_items));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := CONCAT('ALTER TABLE `after_sale_only_refunds` AUTO_INCREMENT = ', (SELECT MAX(id) + 1 FROM after_sale_only_refunds));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := CONCAT('ALTER TABLE `after_sale_returns` AUTO_INCREMENT = ', (SELECT MAX(id) + 1 FROM after_sale_returns));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := CONCAT('ALTER TABLE `after_sale_exchange_returns` AUTO_INCREMENT = ', (SELECT MAX(id) + 1 FROM after_sale_exchange_returns));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := CONCAT('ALTER TABLE `cart` AUTO_INCREMENT = ', (SELECT MAX(id) + 1 FROM cart));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 其余表计数器本来就是正常的（products/categories/stock_alert 等），
-- 但也一并归位，保证「新建数据 id 不再跳号段」。
SET @sql := CONCAT('ALTER TABLE `products` AUTO_INCREMENT = ', (SELECT MAX(id) + 1 FROM products));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := CONCAT('ALTER TABLE `categories` AUTO_INCREMENT = ', (SELECT MAX(id) + 1 FROM categories));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := CONCAT('ALTER TABLE `stock_alert` AUTO_INCREMENT = ', (SELECT MAX(id) + 1 FROM stock_alert));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := CONCAT('ALTER TABLE `admin_menu` AUTO_INCREMENT = ', (SELECT MAX(id) + 1 FROM admin_menu));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := CONCAT('ALTER TABLE `admin_role` AUTO_INCREMENT = ', (SELECT MAX(id) + 1 FROM admin_role));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := CONCAT('ALTER TABLE `member_level` AUTO_INCREMENT = ', (SELECT MAX(id) + 1 FROM member_level));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- ==================== 14. 核对 ====================
-- 14.1 不应再有沙箱段 id
SELECT 'user' t, COUNT(*) 残留 FROM `user` WHERE id >= 100000
UNION ALL SELECT 'admin', COUNT(*) FROM admin WHERE id >= 100000
UNION ALL SELECT 'tags', COUNT(*) FROM tags WHERE id >= 100000
UNION ALL SELECT 'address', COUNT(*) FROM address WHERE id >= 100000
UNION ALL SELECT 'orders', COUNT(*) FROM orders WHERE id >= 100000
UNION ALL SELECT 'order_items', COUNT(*) FROM order_items WHERE id >= 100000
UNION ALL SELECT 'after_sales', COUNT(*) FROM after_sales WHERE id >= 100000
UNION ALL SELECT 'after_sale_items', COUNT(*) FROM after_sale_items WHERE id >= 100000
UNION ALL SELECT 'after_sale_only_refunds', COUNT(*) FROM after_sale_only_refunds WHERE id >= 100000
UNION ALL SELECT 'after_sale_returns', COUNT(*) FROM after_sale_returns WHERE id >= 100000
UNION ALL SELECT 'after_sale_exchange_returns', COUNT(*) FROM after_sale_exchange_returns WHERE id >= 100000
UNION ALL SELECT 'cart', COUNT(*) FROM cart WHERE id >= 100000;

-- 14.2 外键无孤儿：以下应全部为 0
SELECT 'order_items.order_id' ref, COUNT(*) 孤儿 FROM order_items oi LEFT JOIN orders o ON o.id = oi.order_id WHERE o.id IS NULL
UNION ALL SELECT 'after_sales.order_id', COUNT(*) FROM after_sales a LEFT JOIN orders o ON o.id = a.order_id WHERE o.id IS NULL
UNION ALL SELECT 'after_sale_items.after_sale_id', COUNT(*) FROM after_sale_items ai LEFT JOIN after_sales a ON a.id = ai.after_sale_id WHERE a.id IS NULL
UNION ALL SELECT 'after_sale_items.order_item_id', COUNT(*) FROM after_sale_items ai LEFT JOIN order_items oi ON oi.id = ai.order_item_id WHERE oi.id IS NULL
UNION ALL SELECT 'after_sale_only_refunds.after_sale_id', COUNT(*) FROM after_sale_only_refunds ao LEFT JOIN after_sales a ON a.id = ao.after_sale_id WHERE a.id IS NULL
UNION ALL SELECT 'after_sale_returns.after_sale_id', COUNT(*) FROM after_sale_returns ar LEFT JOIN after_sales a ON a.id = ar.after_sale_id WHERE a.id IS NULL
UNION ALL SELECT 'after_sale_exchange_returns.after_sale_id', COUNT(*) FROM after_sale_exchange_returns ae LEFT JOIN after_sales a ON a.id = ae.after_sale_id WHERE a.id IS NULL
UNION ALL SELECT 'orders.user_id', COUNT(*) FROM orders o LEFT JOIN `user` u ON u.id = o.user_id WHERE u.id IS NULL
UNION ALL SELECT 'address.user_id', COUNT(*) FROM address a LEFT JOIN `user` u ON u.id = a.user_id WHERE u.id IS NULL
UNION ALL SELECT 'cart.user_id', COUNT(*) FROM cart c LEFT JOIN `user` u ON u.id = c.user_id WHERE u.id IS NULL
UNION ALL SELECT 'product_reviews.order_id', COUNT(*) FROM product_reviews pr LEFT JOIN orders o ON o.id = pr.order_id WHERE o.id IS NULL
UNION ALL SELECT 'product_reviews.user_id', COUNT(*) FROM product_reviews pr LEFT JOIN `user` u ON u.id = pr.user_id WHERE u.id IS NULL
UNION ALL SELECT 'admin_user_role.admin_id', COUNT(*) FROM admin_user_role ur LEFT JOIN admin a ON a.id = ur.admin_id WHERE a.id IS NULL
-- 注意：tag_id 允许为 NULL（大部分商品没打标签），NULL 不算孤儿
UNION ALL SELECT 'products.tag_id', COUNT(*) FROM products p LEFT JOIN tags t ON t.id = p.tag_id WHERE p.tag_id IS NOT NULL AND t.id IS NULL;

-- 14.3 计数器已归位（下一个 id 应紧跟 max(id)）
-- ⚠️ MySQL 8.0 的 information_schema.TABLES 有统计缓存（默认 1 天），
--    查 AUTO_INCREMENT 前先执行：SET SESSION information_schema_stats_expiry = 0;
--    否则看到的还是执行前的旧值（MySQL 5.7 无缓存，直接查即实时）。
SELECT TABLE_NAME, AUTO_INCREMENT AS 下一个ID FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE' AND AUTO_INCREMENT IS NOT NULL
ORDER BY AUTO_INCREMENT DESC;
