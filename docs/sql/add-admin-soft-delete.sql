-- 后台账号软删：admin 表加 is_deleted 标记
-- 背景：后台账号原来只有 status（1 正常 / 0 停用），没有删除功能；
--       权限点 system:user:remove 在 admin_menu 里已存在，但接口/前端一直没实现。
--       本次补齐：删除 = 软删（is_deleted=1 + status=0），账号从列表消失且立刻踢下线。
-- 执行范围：本地库 + 生产库各跑一次（幂等，重复执行安全）。
-- 生产执行顺序：先跑本 SQL（加列，旧代码不受影响），再发布 auth-service 新版本。

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin' AND COLUMN_NAME = 'is_deleted'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `admin` ADD COLUMN `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT ''是否已删除：0 正常 1 已删除''',
  'SELECT ''admin.is_deleted 已存在，跳过'' AS message'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 校验 1：列结构
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin' AND COLUMN_NAME = 'is_deleted';

-- 校验 2：现有账号应全部是未删除
SELECT id, username, status, is_deleted FROM `admin` ORDER BY id;

-- 校验 3：本地库与生产库应保持一致（两边都是 0 行 is_deleted=1）
SELECT COUNT(*) AS deleted_count FROM `admin` WHERE is_deleted = 1;
