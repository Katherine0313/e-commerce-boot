-- ============================================================================
-- 修正 admin.name：把「角色名」改成「管理员本人姓名」
--
-- 背景：
--   建 RBAC 时把角色名误填进了 admin.name，导致 4 个演示账号的姓名是
--   「管理员 / 运营 / 客服 / 仓储发货」，与 admin_role.role_name 完全重复。
--   表结构本身没问题：admin.name 语义就是【登录人姓名】，
--   角色名只存在于 admin_role.role_name，两者由 admin_user_role 关联。
--
--   表结构无需改动，本脚本只做数据修正 + 字段注释澄清。
--
-- 说明：
--   1. 按 username 匹配更新（uk_admin_username 唯一），不依赖 id，
--      生产/本地 id 未必一致，这样两边都能用同一份脚本。
--   2. 幂等：已改过的账号重复执行不会出错。
--   3. 不影响登录 / 权限：权限一律走 admin_user_role → admin_role.role_key，
--      与 name 无关；name 只用于界面显示和日后操作日志记录操作人。
--   4. 若生产上这些账号已被改成真实姓名，把对应行改成实际姓名即可，
--      或直接跳过该行（脚本按 username 定位，不会误伤别人）。
--
-- 执行方式：
--   mysql -u root -p --default-character-set=utf8mb4 e_commerce_db < docs/sql/rbac/03-fix-admin-name.sql
--   或（走发布通道）./deploy/run-sql-prod.sh docs/sql/rbac/03-fix-admin-name.sql
-- ============================================================================

START TRANSACTION;

-- ---------- 步骤 1：改前快照（先看，确认要改的是这批账号） ----------
SELECT a.id, a.username, a.name AS 改前姓名,
       r.role_name AS 该账号绑定的角色
FROM admin a
LEFT JOIN admin_user_role ur ON ur.admin_id = a.id
LEFT JOIN admin_role r ON r.id = ur.role_id
WHERE a.username IN ('admin', 'operator', 'service', 'warehouse')
ORDER BY a.id;

-- ---------- 步骤 2：修正姓名 ----------
UPDATE admin SET name = '王芳' WHERE username = 'admin';
UPDATE admin SET name = '李强' WHERE username = 'operator';
UPDATE admin SET name = '赵敏' WHERE username = 'service';
UPDATE admin SET name = '孙磊' WHERE username = 'warehouse';

-- ---------- 步骤 3：核对（name 应是姓名，role_name 应是角色名，两者不再重复） ----------
SELECT a.id, a.username, a.name AS 姓名, r.role_name AS 角色
FROM admin a
LEFT JOIN admin_user_role ur ON ur.admin_id = a.id
LEFT JOIN admin_role r ON r.id = ur.role_id
WHERE a.username IN ('admin', 'operator', 'service', 'warehouse')
ORDER BY a.id;

COMMIT;

-- ---------- 步骤 4：字段注释澄清（只改注释，不动数据和类型） ----------
-- 说明：MySQL 5.7 不支持只改 COMMENT，必须完整重述列定义；
--       这里与原定义保持一致（VARCHAR(64) NOT NULL），只把注释从「显示名」改成「管理员姓名」。
ALTER TABLE `admin`
    MODIFY COLUMN `name` VARCHAR(64) NOT NULL COMMENT '管理员姓名';

-- 核对注释
-- SELECT COLUMN_NAME, COLUMN_COMMENT FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin' AND COLUMN_NAME = 'name';


-- ==================== 回滚参考 ====================
-- 回滚到旧的（错误的）角色名：
-- UPDATE admin SET name = '管理员'   WHERE username = 'admin';
-- UPDATE admin SET name = '运营'     WHERE username = 'operator';
-- UPDATE admin SET name = '客服'     WHERE username = 'service';
-- UPDATE admin SET name = '仓储发货' WHERE username = 'warehouse';
-- 回滚字段注释：
-- ALTER TABLE `admin` MODIFY COLUMN `name` VARCHAR(64) NOT NULL COMMENT '显示名';
