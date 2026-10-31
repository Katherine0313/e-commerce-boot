-- ============================================================================
-- 移除 admin.role 冗余字段（RBAC 反规范化清理）
--
-- 背景：
--   admin.role 是 RBAC 落地之前的老字段（单角色字符串），真正的角色关系在
--   admin_user_role + admin_role.role_key。它是传递依赖的冗余副本，且会带来
--   「谁是主角色」的多处不一致（写入按前端传参顺序、读取按 role_id 排序）。
--   本脚本把角色收敛到关联表这一处真相。
--
-- ⚠️ 执行顺序（务必按顺序做，颠倒会导致管理员登不进后台）：
--   步骤 1  补齐 admin_user_role 绑定（本脚本上半部分，先执行并核对结果）
--   步骤 2  部署新版 auth-service（新版不再读写 admin.role）
--   步骤 3  再执行本脚本下半部分的 ALTER TABLE DROP COLUMN
--
--   原因：旧代码的 INSERT 显式写 role 列，列被删后新建管理员会报
--         "Unknown column 'role'"；新代码已不依赖该列，可以安全删除。
--
-- 执行方式：
--   mysql -u root -p --default-character-set=utf8mb4 e_commerce_db < docs/sql/rbac/02-drop-admin-role-column.sql
-- ============================================================================


-- ==================== 步骤 1：补齐角色绑定 ====================

-- 1.1 先查：哪些管理员一个角色都没绑（这些账号删列后会彻底没有权限）
SELECT a.id, a.username, a.name, a.role AS 旧单角色字段,
       (SELECT COUNT(*) FROM admin_user_role ur WHERE ur.admin_id = a.id) AS 已绑角色数
FROM admin a
LEFT JOIN admin_user_role ur ON ur.admin_id = a.id
WHERE ur.admin_id IS NULL
ORDER BY a.id;

-- 1.2 根账号（id=1）兜底绑定 admin 角色。
--     新代码里 id=1 已按超管处理（AuthServiceImpl.loadAuthInfo），
--     这条绑定是双保险，保证 id=1 一定出现在 admin_user_role 里。
INSERT IGNORE INTO `admin_user_role` (`admin_id`, `role_id`)
SELECT id, 1 FROM `admin` WHERE id = 1;

-- 1.3 其余未绑定的账号：按业务角色逐个补（下面以「运营」role_id=2 为例，
--     请按 1.1 查出来的账号实际职责选择 role_id，不要照抄）
-- INSERT IGNORE INTO `admin_user_role` (`admin_id`, `role_id`)
-- SELECT id, 2 FROM `admin` WHERE username = 'operator';

-- 1.4 核对：应该查不出任何「0 角色」的账号
SELECT COUNT(*) AS 仍未绑角色的账号数
FROM admin a
WHERE NOT EXISTS (SELECT 1 FROM admin_user_role ur WHERE ur.admin_id = a.id);


-- ==================== 步骤 3：删除冗余列 ====================
-- ⚠️ 必须确认步骤 2 的新版 auth-service 已经部署上线，再执行下面这条

ALTER TABLE `admin` DROP COLUMN `role`;

-- 核对：列已不存在
-- SELECT COLUMN_NAME FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin' AND COLUMN_NAME = 'role';


-- ==================== 回滚参考 ====================
-- 加回列并回填（加回后新代码仍不写它，只能靠 admin_user_role 生效）
-- ALTER TABLE `admin` ADD COLUMN `role` VARCHAR(32) NOT NULL DEFAULT 'admin'
--     COMMENT '角色（已废弃，角色以 admin_user_role 关联表为准）' AFTER `phone`;
-- UPDATE admin a
--   JOIN admin_user_role ur ON ur.admin_id = a.id
--   JOIN admin_role r ON r.id = ur.role_id
-- SET a.role = (SELECT MIN(r2.role_key) FROM admin_user_role ur2
--                JOIN admin_role r2 ON r2.id = ur2.role_id
--                WHERE ur2.admin_id = a.id);
