-- ============================================================
-- 库存模块 DDL（生产执行）— 步骤 1~3：表结构 + 数据迁移
-- 菜单 INSERT 由 02 号文件负责（需要先拿到新插入 id）
-- ============================================================

-- ===== 1. ALTER product_sku 加预警阈值 =====
ALTER TABLE product_sku
  ADD COLUMN min_stock INT NOT NULL DEFAULT 0 COMMENT '安全库存阈值（0=不预警）',
  ADD COLUMN max_stock INT NULL COMMENT '上限阈值（可选）';

-- ===== 2. 库存流水 =====
CREATE TABLE stock_transaction (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  sku_id          BIGINT        NOT NULL,
  sku_code        VARCHAR(64),
  sku_name        VARCHAR(200),
  spec_text       VARCHAR(200),
  order_item_id   BIGINT,
  stock_order_id  BIGINT,
  type            VARCHAR(20)   NOT NULL,
  reason          VARCHAR(30)   NOT NULL,
  qty             INT           NOT NULL,
  balance_after   INT           NOT NULL,
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

-- ===== 3. 库存单据主表 =====
CREATE TABLE stock_order (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  order_no      VARCHAR(32)   NOT NULL,
  type          VARCHAR(20)   NOT NULL,
  biz_type      VARCHAR(30),
  related_no    VARCHAR(64),
  status        TINYINT       NOT NULL DEFAULT 1,
  total_qty     INT           NOT NULL DEFAULT 0,
  total_amount  DECIMAL(12,2),
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

-- ===== 4. 单据明细 =====
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

-- ===== 5. 旧 stock_alert 阈值迁移到 SKU 级 =====
UPDATE product_sku sku
INNER JOIN stock_alert sa ON sa.product_id = sku.product_id AND sa.alert_enabled = 1
SET sku.min_stock = COALESCE(sa.min_stock, 0),
    sku.max_stock = sa.max_stock
WHERE sa.min_stock IS NOT NULL OR sa.max_stock IS NOT NULL;

SELECT '=== 迁移结果 ===' AS x;
SELECT COUNT(*) AS sku_with_alert FROM product_sku WHERE min_stock > 0;
SELECT COUNT(*) AS sku_total FROM product_sku;
