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
    `name`        VARCHAR(64)  NOT NULL                COMMENT '显示名',
    `phone`       VARCHAR(32)  DEFAULT NULL            COMMENT '手机号',
    `role`        VARCHAR(32)  NOT NULL DEFAULT 'admin' COMMENT '角色：admin=管理员（RBAC 权限表后续补充）',
    `shop_id`     BIGINT       NOT NULL DEFAULT 1      COMMENT '所属店铺（模板店=1）',
    `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 正常 0 禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_admin_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '后台管理员';

-- 种子数据：模板店管理员（与 Node 后端演示账号一致，只读维护演示素材用）
INSERT IGNORE INTO `admin` (`username`, `password`, `name`, `role`, `shop_id`)
VALUES ('admin', 'admin123', '管理员', 'admin', 1);
