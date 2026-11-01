-- 后台管理员表（auth-service，对应 admin 模块）
-- 与 C 端 user 表（小程序/PC 商城顾客）分开：管理员走账号密码，顾客走 openid / 账号。
--
-- 执行方式（任选其一）：
--   mysql -u root -p e_commerce_db < docs/sql/admin.sql
-- 或在 MySQL 客户端里 source docs/sql/admin.sql
--
-- 说明：本脚本只「建表 + 插种子数据」，幂等可重复执行，不清空/重建已有表。

CREATE TABLE IF NOT EXISTS `admin` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(64)  NOT NULL                COMMENT '登录用户名',
    `password`    VARCHAR(128) NOT NULL                COMMENT '密码（开发期明文，生产换 BCrypt）',
    `name`        VARCHAR(64)  NOT NULL                COMMENT '管理员姓名',
    `phone`       VARCHAR(32)  DEFAULT NULL            COMMENT '手机号',
    `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 正常 0 禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_admin_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '后台管理员';

-- 种子数据：模板店管理员（与 Node 后端演示账号一致，只读维护演示素材用）
-- 角色不进 admin 表：角色由 admin_role + admin_user_role 关联表决定
-- name 是【管理员本人姓名】，不是角色名（角色名看 admin_role.role_name）
INSERT IGNORE INTO `admin` (`username`, `password`, `name`)
VALUES ('admin', 'admin123', '王芳');
