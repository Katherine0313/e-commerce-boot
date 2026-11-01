-- ============================================================================
-- 本地 / 生产 库配置数据一致性校验（2026-11-01）
--
-- 用途：按「改本地 MySQL 一定要同步生产」这条规矩，改完库后跑一次这个脚本，
--       两边各跑一遍，输出的指纹必须逐行相同 —— 不同说明线上和本地又对不上了。
--
-- 覆盖的表（运营配置类，不含用户/订单等业务流水）：
--   categories / tags / products.tag_id / products 价格三列 /
--   admin / admin_role / admin_user_role / admin_menu / admin_role_menu / member_level
--
-- 用法：
--   本地：mysql -h127.0.0.1 -ue_commerce_db -p*** --default-character-set=utf8mb4 \
--             e_commerce_db < docs/sql/check-local-prod-parity.sql
--   生产：ssh ecommerce "cd /www/java && set -a && . ./.env && set +a && \
--             mysql -h127.0.0.1 -u$DB_USERNAME -p$DB_PASSWORD \
--             --default-character-set=utf8mb4 -N e_commerce_db" \
--             < docs/sql/check-local-prod-parity.sql
--
--   两份输出比对（去掉告警行与行尾差异）：
--     grep -v "Using a password" 本地.out | tr -d '\r' | sort > l.s
--     grep -v "Using a password" 生产.out | tr -d '\r' | sort > p.s
--     diff l.s p.s        # 无输出 = 一致
--
-- 注意：products.tag_id 与 products 价格列要单独看：
--       tag_id 指纹含全部商品，价格指纹含 id + 售价/划线价/成本价。
-- ============================================================================

SET SESSION group_concat_max_len = 1048576;
SELECT 'categories' AS t, MD5(GROUP_CONCAT(CONCAT_WS('|',id,name,parent_id,sort_order,status,recommend) ORDER BY id)) AS fp FROM categories
UNION ALL
SELECT 'tags', MD5(GROUP_CONCAT(CONCAT_WS('|',id,name,code,type,color,sort_order,status,product_count) ORDER BY id)) FROM tags
UNION ALL
SELECT 'products_tag', MD5(GROUP_CONCAT(CONCAT_WS('|',id,IFNULL(tag_id,0)) ORDER BY id)) FROM products
UNION ALL
SELECT 'admin', MD5(GROUP_CONCAT(CONCAT_WS('|',id,username,name,IFNULL(phone,''),status) ORDER BY id)) FROM admin
UNION ALL
SELECT 'admin_role', MD5(GROUP_CONCAT(CONCAT_WS('|',id,role_key,role_name,status,IFNULL(remark,'')) ORDER BY id)) FROM admin_role
UNION ALL
SELECT 'admin_user_role', MD5(GROUP_CONCAT(CONCAT_WS('|',admin_id,role_id) ORDER BY admin_id,role_id)) FROM admin_user_role
UNION ALL
SELECT 'products_price', MD5(GROUP_CONCAT(CONCAT_WS('|',id,sales_price,market_price,cost_price) ORDER BY id)) FROM products
UNION ALL
SELECT 'admin_menu', MD5(GROUP_CONCAT(CONCAT_WS('|',id,menu_name,parent_id,menu_type,path,component,perms,icon,sort_order,visible,status) ORDER BY id)) FROM admin_menu
UNION ALL
SELECT 'admin_role_menu', MD5(GROUP_CONCAT(CONCAT_WS('|',role_id,menu_id) ORDER BY role_id,menu_id)) FROM admin_role_menu
UNION ALL
SELECT 'member_level', MD5(GROUP_CONCAT(CONCAT_WS('|',id,name,growth_threshold,benefits,sort_order) ORDER BY id)) FROM member_level;
