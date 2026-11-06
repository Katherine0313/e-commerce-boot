-- ============================================================
-- 库存模块菜单（生产 / 本地均可重复执行，幂等）
-- 前置：已执行 01_stock_ddl.sql
-- 结构：库存管理(5) 下 6 个子菜单
--   看板 / SKU库存(原400改造) / 流水 / 入库单 / 出库单 / 调整单
-- ============================================================

-- 1. 旧 product 级预警按钮（菜单410）不再使用，删除
DELETE FROM admin_role_menu WHERE menu_id = 410;
DELETE FROM admin_menu WHERE id = 410;

-- 2. 原「库存列表」(400) 改造为「SKU 库存」
UPDATE admin_menu
SET menu_name  = 'SKU 库存',
    perms      = 'product:sku-stock:list',
    sort_order = 2
WHERE id = 400;

-- 3. INSERT 5 个新子菜单（parent_id=5），已存在则跳过
INSERT INTO admin_menu (parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status)
SELECT 5,'库存看板','C','/stock/dashboard',NULL,'stock:dashboard:view','DashboardOutlined',1,1,1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE parent_id=5 AND path='/stock/dashboard');

INSERT INTO admin_menu (parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status)
SELECT 5,'库存流水','C','/stock/transactions',NULL,'stock:transaction:view','UnorderedListOutlined',3,1,1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE parent_id=5 AND path='/stock/transactions');

INSERT INTO admin_menu (parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status)
SELECT 5,'入库单','C','/stock/receipts',NULL,'stock:receipt:manage','DownloadOutlined',4,1,1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE parent_id=5 AND path='/stock/receipts');

INSERT INTO admin_menu (parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status)
SELECT 5,'出库单','C','/stock/shipments',NULL,'stock:shipment:view','UploadOutlined',5,1,1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE parent_id=5 AND path='/stock/shipments');

INSERT INTO admin_menu (parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status)
SELECT 5,'调整单','C','/stock/adjustments',NULL,'stock:adjust:manage','EditOutlined',6,1,1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE parent_id=5 AND path='/stock/adjustments');

-- 3.1 权限码归一化（幂等，防止早期写入的旧 perms 残留）
UPDATE admin_menu SET perms='stock:dashboard:view'   WHERE parent_id=5 AND path='/stock/dashboard';
UPDATE admin_menu SET perms='stock:transaction:view' WHERE parent_id=5 AND path='/stock/transactions';
UPDATE admin_menu SET perms='stock:receipt:manage'   WHERE parent_id=5 AND path='/stock/receipts';
UPDATE admin_menu SET perms='stock:shipment:view'    WHERE parent_id=5 AND path='/stock/shipments';
UPDATE admin_menu SET perms='stock:adjust:manage'    WHERE parent_id=5 AND path='/stock/adjustments';

-- 4. 角色分配（只给真实存在的角色，幂等）
-- 店铺管理员(1) / 运营(2) / 仓储发货(4)：全部 6 个
INSERT INTO admin_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM admin_role r
JOIN admin_menu m ON (m.id = 5 OR m.parent_id = 5)
WHERE r.id IN (1, 2, 4)
  AND NOT EXISTS (SELECT 1 FROM admin_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id);

-- 销售(6)（仅线上存在该角色）：只读（看板 / SKU库存 / 流水）
INSERT INTO admin_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM admin_role r
JOIN admin_menu m ON m.path IN ('/stock/dashboard','/stock/stock-list','/stock/transactions')
WHERE r.id = 6
  AND NOT EXISTS (SELECT 1 FROM admin_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id);

-- 客服(3)：不给库存权限（不插入）

-- 5. 验证输出
SELECT '=== 库存管理菜单树 ===' AS x;
SELECT id, parent_id, menu_name, menu_type, path, perms, icon, sort_order
FROM admin_menu WHERE id = 5 OR parent_id = 5 ORDER BY sort_order;

SELECT '=== 角色分配 ===' AS x;
SELECT rm.role_id, r.role_name, m.menu_name
FROM admin_role_menu rm
JOIN admin_menu m ON m.id = rm.menu_id
LEFT JOIN admin_role r ON r.id = rm.role_id
WHERE m.id = 5 OR m.parent_id = 5
ORDER BY rm.role_id, m.sort_order;
