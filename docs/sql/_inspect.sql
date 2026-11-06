SELECT '=== products columns ===' AS x;
SHOW COLUMNS FROM products WHERE Field IN ('stock','update_time','id');

SELECT '=== role_menu for 库存管理(5) ===' AS x;
SELECT rm.role_id, r.role_name, rm.menu_id
FROM admin_role_menu rm LEFT JOIN admin_role r ON r.id = rm.role_id
WHERE rm.menu_id IN (5,400,410) ORDER BY rm.role_id, rm.menu_id;
