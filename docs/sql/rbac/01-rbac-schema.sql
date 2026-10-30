-- 后台 RBAC（P0）：角色 / 菜单按钮 / 管理员角色 / 角色菜单
-- 执行方式：
--   mysql -u root -p --default-character-set=utf8mb4 e_commerce_db < docs/sql/rbac/01-rbac-schema.sql
--
-- 说明：
--   1. 只建表 + 插种子，幂等可重复执行；不清空现有数据。
--   2. admin 表继续作为后台账号表，不新建 admin_user。
--   3. 权限码格式：模块:实体:动作，例如 product:product:list、order:order:ship。
--   4. 菜单类型：M 目录 / C 菜单 / F 按钮；按钮只用于权限，不在侧边栏展示。
--   5. 店铺管理员走 role_key=admin 的超级权限，不依赖 role_menu 明细。

-- ==================== 角色 ====================
CREATE TABLE IF NOT EXISTS `admin_role` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '角色 ID',
    `role_key`    VARCHAR(32) NOT NULL                COMMENT '角色标识：admin/operator/service/warehouse',
    `role_name`   VARCHAR(64) NOT NULL                COMMENT '角色名称',
    `status`      TINYINT     NOT NULL DEFAULT 1      COMMENT '状态：1 正常 0 停用',
    `remark`      VARCHAR(255) DEFAULT NULL           COMMENT '备注',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    DEFAULT NULL            COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_admin_role_key` (`role_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '后台角色';

INSERT IGNORE INTO `admin_role` (`id`, `role_key`, `role_name`, `status`, `remark`) VALUES
    (1, 'admin', '店铺管理员', 1, '全部权限'),
    (2, 'operator', '运营', 1, '商品/营销/会员/报表'),
    (3, 'service', '客服', 1, '订单查看/售后处理'),
    (4, 'warehouse', '仓储发货', 1, '订单发货/库存');

-- ==================== 菜单 / 按钮权限 ====================
CREATE TABLE IF NOT EXISTS `admin_menu` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '菜单 ID',
    `parent_id`   BIGINT      NOT NULL DEFAULT 0      COMMENT '父菜单 ID，0 为顶级',
    `menu_name`   VARCHAR(64) NOT NULL                COMMENT '名称',
    `menu_type`   CHAR(1)     NOT NULL                COMMENT '类型：M 目录 C 菜单 F 按钮',
    `path`        VARCHAR(200) DEFAULT NULL           COMMENT '前端路由路径',
    `component`   VARCHAR(200) DEFAULT NULL           COMMENT '前端组件路径（预留动态路由）',
    `perms`       VARCHAR(100) DEFAULT NULL           COMMENT '权限标识',
    `icon`        VARCHAR(64) DEFAULT NULL            COMMENT '图标',
    `sort_order`  INT         NOT NULL DEFAULT 0      COMMENT '排序值',
    `visible`     TINYINT     NOT NULL DEFAULT 1      COMMENT '是否显示：1 显示 0 隐藏',
    `status`      TINYINT     NOT NULL DEFAULT 1      COMMENT '状态：1 正常 0 停用',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    DEFAULT NULL            COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_admin_menu_parent` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '后台菜单与权限';

-- 目录 / 菜单
INSERT IGNORE INTO `admin_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `perms`, `icon`, `sort_order`) VALUES
    (1, 0, '工作台',   'C', '/workplace',              'workplace:view',           'DashboardOutlined',       1),
    (2, 0, '商品管理', 'M', NULL,                        NULL,                       'ReconciliationOutlined',  2),
    (3, 0, '订单管理', 'M', NULL,                        NULL,                       'ShoppingCartOutlined',    3),
    (4, 0, '售后管理', 'M', NULL,                        NULL,                       'CustomerServiceOutlined', 4),
    (5, 0, '库存管理', 'M', NULL,                        NULL,                       'InboxOutlined',           5),
    (6, 0, '经营工具', 'M', NULL,                        NULL,                       'FundOutlined',            6),
    (7, 0, '个人中心', 'C', '/personal',                 'personal:view',            'ProfileOutlined',         7),
    (8, 0, '系统管理', 'M', NULL,                        NULL,                       'SettingOutlined',         8),

    (100, 2, '商品列表', 'C', '/goods/list',             'product:product:list',     NULL, 1),
    (101, 2, '添加商品', 'C', '/goods/add-goods',        'product:product:add',      NULL, 2),
    (102, 2, '商品分类', 'C', '/goods/categories',       'product:category:list',    NULL, 3),
    (103, 2, '商品标签', 'C', '/goods/tag',              'product:tag:list',         NULL, 4),

    (200, 3, '订单列表', 'C', '/orders/order-list',      'order:order:list',         NULL, 1),
    (201, 3, '发货管理', 'C', '/orders/ship',            'order:order:ship',         NULL, 2),

    (300, 4, '售后订单', 'C', '/after-sales/after-sale-list', 'aftersale:aftersale:list', NULL, 1),

    (400, 5, '库存列表', 'C', '/stock/stock-list',       'product:stock:list',       NULL, 1),

    (500, 6, '促销活动', 'C', '/business/promotion',     'business:promotion:list',  NULL, 1),
    (501, 6, '优惠券',   'C', '/business/coupon',        'business:coupon:list',     NULL, 2),
    (502, 6, '会员',     'C', '/business/member',        'business:member:list',     NULL, 3),

    (800, 8, '管理员账号', 'C', '/system/users', 'system:user:list', NULL, 1),
    (801, 8, '角色管理',   'C', '/system/roles', 'system:role:list', NULL, 2),
    (802, 8, '菜单管理',   'C', '/system/menus', 'system:menu:list', NULL, 3),
    (803, 8, '操作日志',   'C', '/system/logs',  'system:log:list',  NULL, 4);

-- 按钮权限（F，仅用于后端/按钮级校验）
INSERT IGNORE INTO `admin_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `perms`, `sort_order`) VALUES
    (110, 100, '商品编辑', 'F', 'product:product:edit',        1),
    (111, 100, '商品删除', 'F', 'product:product:remove',      2),
    (120, 102, '分类新增', 'F', 'product:category:add',        1),
    (121, 102, '分类编辑', 'F', 'product:category:edit',       2),
    (122, 102, '分类删除', 'F', 'product:category:remove',     3),
    (130, 103, '标签新增', 'F', 'product:tag:add',             1),
    (131, 103, '标签编辑', 'F', 'product:tag:edit',            2),
    (132, 103, '标签删除', 'F', 'product:tag:remove',          3),
    (210, 200, '订单详情', 'F', 'order:order:detail',          1),
    (211, 200, '订单修改', 'F', 'order:order:update',          2),
    (212, 200, '订单删除', 'F', 'order:order:remove',          3),
    (213, 200, '订单导出', 'F', 'order:order:export',          4),
    (220, 201, '订单发货', 'F', 'order:order:ship',            1),
    (310, 300, '售后审核', 'F', 'aftersale:aftersale:audit',   1),
    (410, 400, '库存预警设置', 'F', 'product:stock:edit',      1),
    (510, 500, '促销新增', 'F', 'business:promotion:add',      1),
    (511, 500, '促销编辑', 'F', 'business:promotion:edit',     2),
    (512, 500, '促销删除', 'F', 'business:promotion:remove',   3),
    (513, 500, '促销结束', 'F', 'business:promotion:end',      4),
    (520, 501, '优惠券新增', 'F', 'business:coupon:add',       1),
    (521, 501, '优惠券编辑', 'F', 'business:coupon:edit',      2),
    (522, 501, '优惠券删除', 'F', 'business:coupon:remove',    3),
    (523, 501, '优惠券发券', 'F', 'business:coupon:grant',     4),
    (530, 502, '会员编辑', 'F', 'business:member:edit',        1),
    (531, 502, '会员刷新', 'F', 'business:member:refresh',     2),
    (810, 800, '账号新增', 'F', 'system:user:add',             1),
    (811, 800, '账号编辑', 'F', 'system:user:edit',            2),
    (812, 800, '账号删除', 'F', 'system:user:remove',          3),
    (813, 800, '重置密码', 'F', 'system:user:resetPwd',        4),
    (820, 801, '角色新增', 'F', 'system:role:add',             1),
    (821, 801, '角色编辑', 'F', 'system:role:edit',            2),
    (822, 801, '角色删除', 'F', 'system:role:remove',          3),
    (823, 801, '角色分配', 'F', 'system:role:assign',          4),
    (830, 802, '菜单新增', 'F', 'system:menu:add',             1),
    (831, 802, '菜单编辑', 'F', 'system:menu:edit',            2),
    (832, 802, '菜单删除', 'F', 'system:menu:remove',          3);

-- ==================== 管理员-角色 ====================
CREATE TABLE IF NOT EXISTS `admin_user_role` (
    `admin_id` BIGINT NOT NULL COMMENT '管理员 ID',
    `role_id`  BIGINT NOT NULL COMMENT '角色 ID',
    PRIMARY KEY (`admin_id`, `role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '管理员角色关联';

-- ==================== 角色-菜单 ====================
CREATE TABLE IF NOT EXISTS `admin_role_menu` (
    `role_id` BIGINT NOT NULL COMMENT '角色 ID',
    `menu_id` BIGINT NOT NULL COMMENT '菜单 ID',
    PRIMARY KEY (`role_id`, `menu_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '角色菜单关联';

-- ==================== 演示账号（后续可在账号管理里修改/删除） ====================
INSERT IGNORE INTO `admin` (`username`, `password`, `name`, `role`, `status`) VALUES
    ('operator',  'operator123',  '运营',      'operator',  1),
    ('service',   'service123',   '客服',      'service',   1),
    ('warehouse', 'warehouse123', '仓储发货',  'warehouse', 1);

-- 绑定角色：admin 为店铺管理员，其余按 username 绑定
INSERT IGNORE INTO `admin_user_role` (`admin_id`, `role_id`)
SELECT id, 1 FROM `admin` WHERE username = 'admin';
INSERT IGNORE INTO `admin_user_role` (`admin_id`, `role_id`)
SELECT id, 2 FROM `admin` WHERE username = 'operator';
INSERT IGNORE INTO `admin_user_role` (`admin_id`, `role_id`)
SELECT id, 3 FROM `admin` WHERE username = 'service';
INSERT IGNORE INTO `admin_user_role` (`admin_id`, `role_id`)
SELECT id, 4 FROM `admin` WHERE username = 'warehouse';

-- 运营：工作台 + 商品 + 库存 + 经营工具 + 订单只读 + 个人中心
INSERT IGNORE INTO `admin_role_menu` (`role_id`, `menu_id`) VALUES
    (2,1),(2,7),(2,2),(2,100),(2,101),(2,102),(2,103),(2,110),(2,111),
    (2,120),(2,121),(2,122),(2,130),(2,131),(2,132),
    (2,5),(2,400),(2,410),
    (2,6),(2,500),(2,501),(2,502),(2,510),(2,511),(2,512),(2,513),
    (2,520),(2,521),(2,522),(2,523),(2,530),(2,531),
    (2,3),(2,200),(2,210),
    (2,4),(2,300);

-- 客服：工作台 + 订单查看 + 售后审核 + 会员查看 + 个人中心
INSERT IGNORE INTO `admin_role_menu` (`role_id`, `menu_id`) VALUES
    (3,1),(3,7),(3,3),(3,200),(3,210),
    (3,4),(3,300),(3,310),
    (3,6),(3,502);

-- 仓储：工作台 + 订单查看/发货 + 库存 + 个人中心
INSERT IGNORE INTO `admin_role_menu` (`role_id`, `menu_id`) VALUES
    (4,1),(4,7),(4,3),(4,200),(4,210),(4,201),(4,220),
    (4,5),(4,400),(4,410);
