-- ============================================================
-- 库存模块上线 SQL（生产执行）
-- 顺序：先 ALTER + CREATE TABLES → 迁移数据 → 再菜单 INSERT
-- ============================================================

-- ===== 第 1 步：ALTER product_sku 加预警阈值 =====
ALTER TABLE product_sku
  ADD COLUMN min_stock INT NOT NULL DEFAULT 0 COMMENT '安全库存阈值（0=不预警）',
  ADD COLUMN max_stock INT NULL COMMENT '上限阈值（可选）';

-- ===== 第 2 步：CREATE 三张新表 =====

-- 库存流水（审计唯一来源）
CREATE TABLE stock_transaction (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  sku_id          BIGINT        NOT NULL,
  sku_code        VARCHAR(64),
  sku_name        VARCHAR(200),
  spec_text       VARCHAR(200),
  order_item_id   BIGINT,
  stock_order_id  BIGINT,
  type            VARCHAR(20)   NOT NULL,              -- IN / OUT / ADJUST
  reason          VARCHAR(30)   NOT NULL,              -- 销售出库/采购入库/退货归还/盘点调整/手工补录
  qty             INT           NOT NULL,              -- 正数，变动量
  balance_after   INT           NOT NULL,              -- 结存快照（审计关键）
  operator_id     BIGINT,
  operator_name   VARCHAR(64),
  remark          VARCHAR(500),
  create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_sku_time (sku_id, create_time),
  KEY idx_order_item (order_item_id),
  KEY idx_order (stock_order_id),
  KEY idx_type_time (type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存流水';

-- 库存单据主表
CREATE TABLE stock_order (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  order_no      VARCHAR(32)   NOT NULL,                 -- RK/CK/PD + yyyyMMdd + 4位序号
  type          VARCHAR(20)   NOT NULL,                 -- RECEIPT / SHIPMENT / ADJUST
  biz_type      VARCHAR(30),                           -- 采购入库/退货归还/销售出库/盘点/手工补录
  related_no    VARCHAR(64),                           -- 关联订单号/售后单号
  status        TINYINT       NOT NULL DEFAULT 1,      -- 1已完成 / 0已取消（P0 无草稿）
  total_qty     INT           NOT NULL DEFAULT 0,
  total_amount  DECIMAL(12,2),                          -- 采购入库成本总额（可选）
  remark        VARCHAR(500),
  operator_id   BIGINT,
  operator_name VARCHAR(64),
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_no (order_no),
  KEY idx_related (related_no),
  KEY idx_type_time (type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存单据主表';

-- 单据明细
CREATE TABLE stock_order_item (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  order_id      BIGINT        NOT NULL,
  sku_id        BIGINT        NOT NULL,
  sku_code      VARCHAR(64),
  sku_name      VARCHAR(200),
  spec_text     VARCHAR(200),
  qty           INT           NOT NULL,
  cost_price    DECIMAL(10,2),
  PRIMARY KEY (id),
  KEY idx_order (order_id),
  KEY idx_sku (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存单据明细';

-- ===== 第 3 步：旧 stock_alert 数据迁移到 product_sku =====
-- 把 product 级预警阈值复制给同一商品下所有 SKU
UPDATE product_sku sku
INNER JOIN stock_alert sa ON sa.product_id = sku.product_id AND sa.alert_enabled = 1
SET sku.min_stock = sa.min_stock;

-- ===== 第 4 步：查现有 /stock 一级菜单 id =====
-- ⚠️ 执行到这里停一下！先跑这条 SELECT：
--   SELECT id, parent_id, menu_name FROM admin_menu WHERE path = '/stock' AND menu_type = 'M';
-- 假设查出来 id = X（下面 SQL 里的 X 替换成你实际查到的值）
-- 下面菜单 INSERT 里的 X 必须是这个值，否则子菜单挂不到正确的父级

-- ===== 第 5 步：UPDATE 旧 "库存列表" 子菜单改标题 =====
-- 先查旧子菜单的 path： SELECT id, menu_name, path FROM admin_menu WHERE parent_id = X;
-- 假设旧子菜单 path = 'stock-list'，下面按实际 path 改：
UPDATE admin_menu
SET menu_name = 'SKU 库存', perms = 'product:sku-stock:list'
WHERE parent_id = X AND menu_name = '库存列表';

-- ===== 第 6 步：INSERT 5 个新子菜单 =====
INSERT INTO admin_menu (parent_id, menu_name, menu_type, path, perms, icon, sort_order, visible, status)
VALUES
  (X, '库存看板', 'C', 'dashboard',    'stock:dashboard:view',    'DashboardOutlined',   1, 1, 1),
  (X, '库存流水', 'C', 'transactions', 'stock:transaction:view',  'UnorderedListOutlined', 3, 1, 1),
  (X, '入库单',   'C', 'receipts',     'stock:receipt:*',         'DownloadOutlined',    4, 1, 1),
  (X, '出库单',   'C', 'shipments',    'stock:shipment:*',        'UploadOutlined',      5, 1, 1),
  (X, '调整单',   'C', 'adjustments',  'stock:adjust:*',          'EditOutlined',        6, 1, 1);

-- ===== 第 7 步：角色菜单分配 =====
-- 先查新插入菜单的 id：
--   SELECT id, menu_name FROM admin_menu WHERE parent_id = X AND sort_order IN (1,3,4,5,6);
-- 假设查出 5 个 id 分别是 M1(看板), M2(流水), M3(入库), M4(出库), M5(调整)
-- 店铺管理员(1) + 运营(2)：全部新菜单
INSERT INTO admin_role_menu (role_id, menu_id) VALUES
  (1, M1), (1, M2), (1, M3), (1, M4), (1, M5),
  (2, M1), (2, M2), (2, M3), (2, M4), (2, M5),
  (4, M1), (4, M2), (4, M3), (4, M4), (4, M5);
-- 客服(3)不给（维持现状）
