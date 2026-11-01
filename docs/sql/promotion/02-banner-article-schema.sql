-- ============================================================
-- 经营工具：Banner 管理 + 文章管理（2026-11-01）
--
-- 约定对齐本项目现有表：
--   * 软删除列 is_deleted（0 正常 / 1 已删除），没有 deleted
--   * 只有 create_time / update_time，没有 creator / updater / tenant_id
--   * 排序列 sort_order，状态列 status（tinyint，1 启用 / 0 禁用）
--   * 布尔用 tinyint，不用 bit(1)；主键 bigint 有符号自增
--   * utf8mb4_general_ci（生产是 MySQL 5.7，不支持 0900_ai_ci）
--
-- 本脚本幂等：可重复执行。执行前请先备份数据库。
--   本地：mysql -u<user> -p<pass> <db> < 02-banner-article-schema.sql
--   生产：先 mysqldump 备份，再执行同一份
-- ============================================================

-- ------------------------------------------------------------
-- 1. Banner 广告位表
--    一条记录只服务一个位置，终端由 position 决定，所以只存一张图
--    position：1 小程序首页 / 2 小程序分类页 / 11 PC商城首页
--    link_type：0 不跳转 / 1 商品 / 2 文章 / 3 外部链接
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `promotion_banner` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Banner ID',
    `title`        VARCHAR(64)  NOT NULL DEFAULT ''      COMMENT '标题',
    `image`        VARCHAR(255) NOT NULL DEFAULT ''      COMMENT '图片地址（相对路径，如 /image/banner/xxx.webp）',
    `position`     TINYINT      NOT NULL DEFAULT 1       COMMENT '广告位置：1 小程序首页 2 小程序分类页 11 PC商城首页',
    `link_type`    TINYINT      NOT NULL DEFAULT 0       COMMENT '跳转类型：0 不跳转 1 商品 2 文章 3 外部链接',
    `link_value`   VARCHAR(255) NOT NULL DEFAULT ''      COMMENT '跳转目标：商品ID / 文章ID / 外链URL',
    `sort_order`   INT          NOT NULL DEFAULT 0       COMMENT '排序，越小越靠前',
    `status`       TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：1 启用 0 禁用',
    `browse_count` INT          NOT NULL DEFAULT 0       COMMENT '点击次数',
    `is_deleted`   TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 正常 1 已删除',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     DEFAULT NULL            COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_promotion_banner_query` (`position`, `status`, `is_deleted`, `sort_order`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'Banner 广告位表';

-- ------------------------------------------------------------
-- 2. 文章分类表（只在后台归类用，C 端不展示）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `promotion_article_category` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文章分类 ID',
    `name`        VARCHAR(64)  NOT NULL                COMMENT '分类名称',
    `pic_url`     VARCHAR(255) NOT NULL DEFAULT ''     COMMENT '分类图标（可选）',
    `sort_order`  INT          NOT NULL DEFAULT 0      COMMENT '排序，越小越靠前',
    `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 0 禁用',
    `is_deleted`  TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 1 已删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT NULL            COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '文章分类表';

-- ------------------------------------------------------------
-- 3. 文章表（关于我们 / 隐私政策 / 店铺声明 / 常见问题这类固定图文）
--    title 唯一（含逻辑删除的记录）：小程序按 title 查详情，重名会查错
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `promotion_article` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文章 ID',
    `category_id`      BIGINT       NOT NULL DEFAULT 0      COMMENT '文章分类 ID，0 表示未分类',
    `title`            VARCHAR(128) NOT NULL                COMMENT '标题（唯一，逻辑删除后仍占用）',
    `author`           VARCHAR(64)  NOT NULL DEFAULT ''     COMMENT '作者',
    `cover`            VARCHAR(255) NOT NULL DEFAULT ''     COMMENT '封面图地址',
    `content`          LONGTEXT     NOT NULL                COMMENT '正文 HTML',
    `introduction`     VARCHAR(255) NOT NULL DEFAULT ''     COMMENT '简介',
    `sort_order`       INT          NOT NULL DEFAULT 0      COMMENT '排序，越小越靠前',
    `status`           TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：1 启用 0 禁用',
    `recommend_hot`    TINYINT      NOT NULL DEFAULT 0      COMMENT '是否热门（小程序推荐位）',
    `recommend_banner` TINYINT      NOT NULL DEFAULT 0      COMMENT '是否轮播（小程序文章轮播位）',
    `browse_count`     INT          NOT NULL DEFAULT 0      COMMENT '浏览量',
    `spu_id`           BIGINT       NOT NULL DEFAULT 0      COMMENT '关联商品 ID，0 表示不关联',
    `is_deleted`       TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 1 已删除',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     DEFAULT NULL            COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_promotion_article_category` (`category_id`),
    KEY `idx_promotion_article_title` (`title`),
    KEY `idx_promotion_article_status` (`status`, `is_deleted`, `sort_order`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '文章表';

-- ------------------------------------------------------------
-- 4. 后台菜单与权限
--    经营工具（id=6）下新增 Banner管理（503）、文章管理（504）
--    按钮权限 540~542（Banner）、550~552（文章）
--    文章分类不单独建菜单，分类的增删改复用文章权限
-- ------------------------------------------------------------
INSERT IGNORE INTO `admin_menu`
    (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `perms`, `icon`, `sort_order`)
VALUES
    (503, 6, 'Banner管理', 'C', '/business/banner',  'business:banner:list',  NULL, 4),
    (504, 6, '文章管理',   'C', '/business/article', 'business:article:list', NULL, 5);

INSERT IGNORE INTO `admin_menu`
    (`id`, `parent_id`, `menu_name`, `menu_type`, `perms`, `sort_order`)
VALUES
    (540, 503, 'Banner新增', 'F', 'business:banner:add',    1),
    (541, 503, 'Banner编辑', 'F', 'business:banner:edit',   2),
    (542, 503, 'Banner删除', 'F', 'business:banner:remove', 3),
    (550, 504, '文章新增',  'F', 'business:article:add',    1),
    (551, 504, '文章编辑',  'F', 'business:article:edit',   2),
    (552, 504, '文章删除',  'F', 'business:article:remove', 3);

-- ------------------------------------------------------------
-- 5. 把新菜单授权给运营角色
--    与既有约定一致：经营工具下的菜单（促销活动/优惠券/会员）都挂在运营角色上；
--    店铺管理员是超管（*:*:*），不需要在这里授权
-- ------------------------------------------------------------
INSERT IGNORE INTO `admin_role_menu` (`role_id`, `menu_id`) VALUES
    (2, 503), (2, 504),
    (2, 540), (2, 541), (2, 542),
    (2, 550), (2, 551), (2, 552);
