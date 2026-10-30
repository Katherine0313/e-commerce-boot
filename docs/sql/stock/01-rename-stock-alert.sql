-- 库存表改名：stock → stock_alert
-- 原因：该表只存库存预警配置（min_stock/max_stock/alert_enabled），当前库存仍在 products.stock，
--       stock 这个名字容易被误解为库存数量表。
-- 执行方式：
--   mysql -u root -p --default-character-set=utf8mb4 e_commerce_db < docs/sql/stock/01-rename-stock-alert.sql
-- 说明：幂等可重复执行；只改表名/索引名/外键名，不修改任何数据。

SET NAMES utf8mb4;
DELIMITER $$

-- 1. 表名 stock → stock_alert
DROP PROCEDURE IF EXISTS _rename_stock_table$$
CREATE PROCEDURE _rename_stock_table()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.TABLES
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stock')
     AND NOT EXISTS (SELECT 1 FROM information_schema.TABLES
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stock_alert') THEN
    RENAME TABLE `stock` TO `stock_alert`;
  END IF;
END$$

-- 2. 唯一索引 uk_stock_product → uk_stock_alert_product
DROP PROCEDURE IF EXISTS _rename_stock_index$$
CREATE PROCEDURE _rename_stock_index()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stock_alert'
               AND INDEX_NAME = 'uk_stock_product') THEN
    ALTER TABLE `stock_alert` RENAME INDEX `uk_stock_product` TO `uk_stock_alert_product`;
  END IF;
END$$

-- 3. 外键 fk_stock_product → fk_stock_alert_product
DROP PROCEDURE IF EXISTS _rename_stock_fk$$
CREATE PROCEDURE _rename_stock_fk()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
             WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'stock_alert'
               AND CONSTRAINT_NAME = 'fk_stock_product' AND CONSTRAINT_TYPE = 'FOREIGN KEY') THEN
    ALTER TABLE `stock_alert`
      DROP FOREIGN KEY `fk_stock_product`,
      ADD CONSTRAINT `fk_stock_alert_product`
        FOREIGN KEY (`product_id`) REFERENCES `products`(`id`) ON DELETE CASCADE;
  END IF;
END$$

DELIMITER ;

CALL _rename_stock_table();
CALL _rename_stock_index();
CALL _rename_stock_fk();

DROP PROCEDURE IF EXISTS _rename_stock_table;
DROP PROCEDURE IF EXISTS _rename_stock_index;
DROP PROCEDURE IF EXISTS _rename_stock_fk;

-- 自检：应只剩 stock_alert，且索引/外键为新名字
SELECT TABLE_NAME FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('stock', 'stock_alert');
SELECT INDEX_NAME FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stock_alert' GROUP BY INDEX_NAME;
SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'stock_alert' AND CONSTRAINT_TYPE = 'FOREIGN KEY';
