mysql -h127.0.0.1 -ue_commerce_db -p123456 --default-character-set=utf8mb4 e_commerce_db << 'EOF'
SELECT 'SKU阈值' AS item, COUNT(*) AS total, SUM(CASE WHEN min_stock=50 AND max_stock=500 THEN 1 ELSE 0 END) AS ok_cnt FROM product_sku WHERE status=1;
SELECT 'body留图' AS item, COUNT(*) AS total, SUM(CASE WHEN body IS NOT NULL AND body != '' AND body REGEXP '<img' AND NOT body REGEXP '<p[^>]*>[^<：:]+[：:]' THEN 1 ELSE 0 END) AS ok_cnt FROM products;
EOF
