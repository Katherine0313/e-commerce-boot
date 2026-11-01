-- ============================================================================
-- 本地 → 生产 同步（2026-11-01）
--
-- 背景：docs/sql/user-pc-auth.sql 的头部写着「状态：2026-10-31 已在本地与生产库
--       执行完毕」，但生产库实际并没有建出 user.uk_user_username 这个唯一索引
--       （本地有、生产缺）。PC 商城（xiaojiecpn.com）、小程序、后台共用同一个
--       user 表，用户名要全局唯一，必须靠这个唯一索引兜底 —— 否则同名注册会出现
--       两条记录，登录时按用户名 selectOne 会抛 TooManyResultsException。
--       这正是「改了本地没同步生产，线上永远对不上」的一个实例。
--
-- 安全性：生产 user 表当前 username 全部为 NULL（51 行，0 个非空），MySQL 的
--       UNIQUE 索引允许存在多个 NULL，所以建索引不会因重复值失败、不会锁表超时。
--
-- 幂等：先用 information_schema.STATISTICS 判断索引是否已存在，存在就跳过，
--       脚本可以重复执行。
--
-- 执行方式（服务器上）：
--   mysql -h127.0.0.1 -u$DB_USERNAME -p$DB_PASSWORD --default-character-set=utf8mb4 \
--         e_commerce_db < sync-local-to-prod.sql
-- ============================================================================

SET @idx_exists := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'user'
    AND INDEX_NAME   = 'uk_user_username'
);

SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE `user` ADD UNIQUE INDEX `uk_user_username` (`username`)',
  'SELECT ''uk_user_username 已存在，跳过'' AS message'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 校验 1：索引应存在且为唯一索引（NON_UNIQUE = 0）
SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME   = 'user'
  AND INDEX_NAME   = 'uk_user_username';

-- 校验 2：用户名不允许重复（期望 0 行）
SELECT username, COUNT(*) AS cnt
FROM `user`
WHERE username IS NOT NULL
GROUP BY username
HAVING COUNT(*) > 1;

-- 校验 3：索引建好后用户名仍全部为空（本次没有真实 PC 用户，不影响存量登录）
SELECT COUNT(*) AS 用户总数, COUNT(username) AS 已设用户名数 FROM `user`;
