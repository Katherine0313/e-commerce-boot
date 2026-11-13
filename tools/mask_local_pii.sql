-- =============================================================================
-- 本地库 PII 脱敏：手机号 / 微信 openid / 昵称 / PC 用户名 / 后台日志 IP 与账号
--
-- 用途：导出脱敏数据（demo 数据、对外交接、公开仓库种子）之前，把本地开发库里的
--       个人可识别信息打码。导出命令见文件末尾。
--
-- ⚠⚠ 只对本地开发库执行。**绝对不能同步到生产** —— 生产库是真实用户数据，
--    把这份脚本同步过去等于把全站手机号、微信身份、姓名全部毁掉。
--
-- 掩码规则（全部是纯函数：同一个原值在任何表都得到同一个掩码值，跨表关联不破）：
--   手机号           138****8000              前 3 后 4
--   微信 openid      o + MD5 前 27 位          只替换「真实微信 openid」
--   user.name        演示用户<id>
--   user.username    demo_user_<id>            按 id 生成，天然唯一，不撞 uk_user_username
--   日志账号         admin→admin_demo、saler123→saler_demo、其余 op_<MD5前6位>
--   日志 IP          192.168.x.y               内网段
--
-- 为什么 openid 只替换「真实微信 openid」：
--   user.openid 里有两类值 —— 真实微信下发的（'o' 开头 28 位）和代码生成的占位符
--   （PC 账号的 'pc_' 前缀、mock 登录的 'dev_mock_openid'）。占位符本身就是假数据，
--   没必要脱敏；而且替换掉它们会导致本地 mock 登录 / PC 端登录按 openid 查不到用户
--   而重新注册，把现有 PC 账号的关联搞断。
--
-- 原值备份：写入 _pii_backup 表（只在库里，不落盘、导出时用 --ignore-table 排除）
--   还原：见文件末尾的还原语句。
-- =============================================================================

-- ---------- 1. 备份表 ----------
CREATE TABLE IF NOT EXISTS `_pii_backup` (
  `tbl`     VARCHAR(64)  NOT NULL COMMENT '原表名',
  `col`     VARCHAR(64)  NOT NULL COMMENT '原字段名',
  `row_id`  BIGINT       NOT NULL COMMENT '原行 id',
  `orig_val` VARCHAR(255) NOT NULL COMMENT '掩码前的原值',
  UNIQUE KEY `uk_tbl_col_id` (`tbl`, `col`, `row_id`),
  KEY `idx_tbl_col_val` (`tbl`, `col`, `orig_val`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------- 2. 收集原值 ----------

-- 手机号（5 张表，跨表同一个原号 → 同一个掩码值）
INSERT IGNORE INTO `_pii_backup` (`tbl`,`col`,`row_id`,`orig_val`)
SELECT 'user',     'phone',   id, phone   FROM user     WHERE phone   IS NOT NULL AND phone   <> '';
INSERT IGNORE INTO `_pii_backup` (`tbl`,`col`,`row_id`,`orig_val`)
SELECT 'address',  'phone',   id, phone   FROM address  WHERE phone   IS NOT NULL AND phone   <> '';
INSERT IGNORE INTO `_pii_backup` (`tbl`,`col`,`row_id`,`orig_val`)
SELECT 'orders',   'phone',   id, phone   FROM orders   WHERE phone   IS NOT NULL AND phone   <> '';
INSERT IGNORE INTO `_pii_backup` (`tbl`,`col`,`row_id`,`orig_val`)
SELECT 'admin',    'phone',   id, phone   FROM admin    WHERE phone   IS NOT NULL AND phone   <> '';
INSERT IGNORE INTO `_pii_backup` (`tbl`,`col`,`row_id`,`orig_val`)
SELECT 'feedback', 'contact', id, contact FROM feedback WHERE contact IS NOT NULL AND contact <> '';

-- 昵称 / PC 用户名
INSERT IGNORE INTO `_pii_backup` (`tbl`,`col`,`row_id`,`orig_val`)
SELECT 'user', 'name', id, name FROM user WHERE name IS NOT NULL AND name <> '';
INSERT IGNORE INTO `_pii_backup` (`tbl`,`col`,`row_id`,`orig_val`)
SELECT 'user', 'username', id, username FROM user WHERE username IS NOT NULL AND username <> '';

-- 微信 openid：只收真实微信下发的（'o' 开头且 28 位），占位符（pc_ / dev_mock_）跳过
INSERT IGNORE INTO `_pii_backup` (`tbl`,`col`,`row_id`,`orig_val`)
SELECT 'user', 'openid', id, openid FROM user
WHERE openid LIKE 'o%' AND CHAR_LENGTH(openid) = 28;

-- 后台登录日志 / 操作日志：客户端 IP + 操作人账号
INSERT IGNORE INTO `_pii_backup` (`tbl`,`col`,`row_id`,`orig_val`)
SELECT 'admin_login_log', 'ip', id, ip FROM admin_login_log WHERE ip IS NOT NULL AND ip <> '';
INSERT IGNORE INTO `_pii_backup` (`tbl`,`col`,`row_id`,`orig_val`)
SELECT 'admin_login_log', 'username', id, username FROM admin_login_log WHERE username IS NOT NULL AND username <> '';
INSERT IGNORE INTO `_pii_backup` (`tbl`,`col`,`row_id`,`orig_val`)
SELECT 'admin_operation_log', 'ip', id, ip FROM admin_operation_log WHERE ip IS NOT NULL AND ip <> '';
INSERT IGNORE INTO `_pii_backup` (`tbl`,`col`,`row_id`,`orig_val`)
SELECT 'admin_operation_log', 'username', id, username FROM admin_operation_log WHERE username IS NOT NULL AND username <> '';

-- ---------- 3. 执行掩码 ----------

-- 3.1 手机号（5 张表）
UPDATE user u JOIN `_pii_backup` b ON b.`tbl`='user' AND b.`col`='phone' AND b.`row_id`=u.id
SET u.phone = CONCAT(LEFT(b.orig_val,3),'****',RIGHT(b.orig_val,4));

UPDATE address a JOIN `_pii_backup` b ON b.`tbl`='address' AND b.`col`='phone' AND b.`row_id`=a.id
SET a.phone = CONCAT(LEFT(b.orig_val,3),'****',RIGHT(b.orig_val,4));

UPDATE orders o JOIN `_pii_backup` b ON b.`tbl`='orders' AND b.`col`='phone' AND b.`row_id`=o.id
SET o.phone = CONCAT(LEFT(b.orig_val,3),'****',RIGHT(b.orig_val,4));

UPDATE admin ad JOIN `_pii_backup` b ON b.`tbl`='admin' AND b.`col`='phone' AND b.`row_id`=ad.id
SET ad.phone = CONCAT(LEFT(b.orig_val,3),'****',RIGHT(b.orig_val,4));

UPDATE feedback f JOIN `_pii_backup` b ON b.`tbl`='feedback' AND b.`col`='contact' AND b.`row_id`=f.id
SET f.contact = CONCAT(LEFT(b.orig_val,3),'****',RIGHT(b.orig_val,4));

-- 3.2 微信 openid（只动真实微信 openid）
UPDATE user u JOIN `_pii_backup` b ON b.`tbl`='user' AND b.`col`='openid' AND b.`row_id`=u.id
SET u.openid = CONCAT('o', SUBSTRING(MD5(CONCAT('mask-', b.orig_val)), 1, 27));

-- 3.3 昵称 / PC 用户名
UPDATE user u JOIN `_pii_backup` b ON b.`tbl`='user' AND b.`col`='name' AND b.`row_id`=u.id
SET u.name = CONCAT('演示用户', u.id);

UPDATE user u JOIN `_pii_backup` b ON b.`tbl`='user' AND b.`col`='username' AND b.`row_id`=u.id
SET u.username = CONCAT('demo_user_', u.id);

-- 3.4 后台日志 IP：统一换成内网段
UPDATE admin_login_log l JOIN `_pii_backup` b ON b.`tbl`='admin_login_log' AND b.`col`='ip' AND b.`row_id`=l.id
SET l.ip = CONCAT('192.168.', MOD(CRC32(b.orig_val), 200) + 20, '.', MOD(CRC32(CONCAT('ip2-', b.orig_val)), 200) + 20);

UPDATE admin_operation_log l JOIN `_pii_backup` b ON b.`tbl`='admin_operation_log' AND b.`col`='ip' AND b.`row_id`=l.id
SET l.ip = CONCAT('192.168.', MOD(CRC32(b.orig_val), 200) + 20, '.', MOD(CRC32(CONCAT('ip2-', b.orig_val)), 200) + 20);

-- 3.5 后台日志操作人账号（真实姓名也要脱敏）
UPDATE admin_login_log l JOIN `_pii_backup` b ON b.`tbl`='admin_login_log' AND b.`col`='username' AND b.`row_id`=l.id
SET l.username = CASE b.orig_val
      WHEN 'admin'    THEN 'admin_demo'
      WHEN 'saler123' THEN 'saler_demo'
      ELSE CONCAT('op_', SUBSTRING(MD5(CONCAT('mask-', b.orig_val)), 1, 8))
    END;

UPDATE admin_operation_log l JOIN `_pii_backup` b ON b.`tbl`='admin_operation_log' AND b.`col`='username' AND b.`row_id`=l.id
SET l.username = CASE b.orig_val
      WHEN 'admin'    THEN 'admin_demo'
      WHEN 'saler123' THEN 'saler_demo'
      ELSE CONCAT('op_', SUBSTRING(MD5(CONCAT('mask-', b.orig_val)), 1, 8))
    END;

-- ---------- 4. 导出脱敏数据（备份表要排除掉） ----------
-- mysqldump -h127.0.0.1 -P3306 -u<user> -p<pass> --default-character-set=utf8mb4 \
--   --single-transaction --ignore-table=e_commerce_db._pii_backup e_commerce_db > e_commerce_db_demo.sql
--
-- ---------- 5. 还原（把原值写回去，需要时执行） ----------
-- UPDATE user u JOIN `_pii_backup` b ON b.`tbl`='user' AND b.`col`='phone' AND b.`row_id`=u.id
--   SET u.phone = b.orig_val;
-- UPDATE user u JOIN `_pii_backup` b ON b.`tbl`='user' AND b.`col`='name' AND b.`row_id`=u.id
--   SET u.name = b.orig_val;
-- UPDATE user u JOIN `_pii_backup` b ON b.`tbl`='user' AND b.`col`='username' AND b.`row_id`=u.id
--   SET u.username = b.orig_val;
-- UPDATE user u JOIN `_pii_backup` b ON b.`tbl`='user' AND b.`col`='openid' AND b.`row_id`=u.id
--   SET u.openid = b.orig_val;
-- ... 其余表同理，把 CONCAT(...) 掩码表达式换回 b.orig_val 即可
