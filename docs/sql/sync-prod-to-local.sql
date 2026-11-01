-- ============================================================================
-- 生产 → 本地 同步（2026-11-01）
--
-- 背景：审计本地与生产库时发现，两侧的「运营配置」被各自在后台控制台改过，方向相反：
--   1. categories.recommend
--      生产：101 苹果手机 / 201 游戏本 / 301 iPad / 302 安卓平板 / 501 智能手表
--            / 601 微单相机 / 801 智能音箱 = 1，102 安卓手机 = 0
--      本地：仍是种子脚本 02-category-refactor.sql 的初始集合
--            （101 102 201 301 501 601），脚本注释写着「与本地一致」，但生产被人手改过
--      → 以生产为准。
--   2. tags / products.tag_id
--      生产：只有「最新」一个标签，绑定 10 件商品
--            （1 8 9 10 11 12 18 24 26 28）
--      本地：多了一个演示标签「爆款」（2026-10-29 13:24 在本地后台点的），
--            绑定 17/42，同时把 10/11 解绑、把 4 绑上
--      → 以生产为准，删掉本地演示标签、恢复绑定。
--   3. admin / admin_role / admin_user_role
--      生产多一组「客服2」：角色 service1（2026-10-30 16:13 建）、
--      账号 11111（2026-10-30 16:14 建，已禁用）+ 账号-角色关联
--      → 本地补上。
--
-- 方向说明：本次不往生产写任何运营配置 —— 首页推荐分类、标签绑定都是线上正在
--           展示的东西，改动会影响真实用户，一律以生产为准往本地对齐；
--           生产侧只补结构缺陷（见 sync-local-to-prod.sql）。
--
-- 执行方式（本地库）：
--   mysql -h127.0.0.1 -ue_commerce_db -p123456 --default-character-set=utf8mb4 \
--         e_commerce_db < sync-prod-to-local.sql
-- ============================================================================

-- 1) 首页推荐分类：完全对齐生产
UPDATE categories SET recommend = 1
WHERE id IN (101, 201, 301, 302, 501, 601, 801);
UPDATE categories SET recommend = 0
WHERE id NOT IN (101, 201, 301, 302, 501, 601, 801);

-- 2) 商品标签：完全对齐生产
-- 2.1 解绑并删除本地演示标签「爆款」（先解绑，避免依赖外键 ON DELETE SET NULL）
UPDATE products SET tag_id = NULL WHERE tag_id = 2;
DELETE FROM tags WHERE id = 2 AND name = '爆款';

-- 2.2 恢复生产绑定：4 解绑、10/11 绑定「最新」(id=1)、17/42 无标签
UPDATE products SET tag_id = NULL WHERE id IN (4, 17, 42);
UPDATE products SET tag_id = 1    WHERE id IN (10, 11);

-- 2.3 标签商品数按实际绑定重算（物化计数）
UPDATE tags
SET product_count = (SELECT COUNT(*) FROM products p WHERE p.tag_id = tags.id);

-- 3) 角色与账号：补齐生产的第 5 组「客服2」
-- 3.1 角色（可重复执行）
INSERT INTO admin_role (id, role_key, role_name, status, remark, create_time, update_time)
VALUES (5, 'service1', '客服2', 1, '', '2026-10-30 16:13:09', '2026-10-30 16:13:09')
ON DUPLICATE KEY UPDATE
  role_name = VALUES(role_name),
  status    = VALUES(status),
  remark    = VALUES(remark);

-- 3.2 账号：密码是 BCrypt 哈希，脚本里不落盘，用下面这条管道命令从生产原样搬运
--     （哈希直接进本地 mysql，不经过对话记录、也不留在任何文件里）：
--
--   ssh ecommerce "cd /www/java && set -a && . ./.env && set +a && mysql -h127.0.0.1 -u\"\$DB_USERNAME\" -p\"\$DB_PASSWORD\" --default-character-set=utf8mb4 --batch --skip-column-names e_commerce_db" \
--     < docs/sql/gen-admin5-transfer.sql \
--   | mysql -h127.0.0.1 -ue_commerce_db -p123456 --default-character-set=utf8mb4 e_commerce_db
--
-- 3.3 账号-角色关联（可重复执行）
INSERT IGNORE INTO admin_user_role (admin_id, role_id) VALUES (5, 5);

-- 3.4 管理员手机号：生产的 admin id=1（王芳）没留手机号，本地种子数据里是 13800138000
--     → 以生产为准置空（本地登录走用户名密码，置空不影响本地开发）
UPDATE admin SET phone = NULL WHERE id = 1 AND username = 'admin';

-- ---------------------------------------------------------------------------
-- 校验
-- ---------------------------------------------------------------------------
-- 校验 1：推荐分类应与生产一致
SELECT id, name, recommend FROM categories ORDER BY id;

-- 校验 2：标签只剩「最新」，绑定 10 件商品
SELECT t.id, t.name, t.product_count,
       (SELECT COUNT(*) FROM products p WHERE p.tag_id = t.id) AS 实际绑定数
FROM tags t ORDER BY t.id;

-- 校验 3：账号与角色各 5 条，关联 5 条
SELECT id, username, name, status FROM admin ORDER BY id;
SELECT id, role_key, role_name, status FROM admin_role ORDER BY id;
SELECT * FROM admin_user_role ORDER BY admin_id, role_id;
