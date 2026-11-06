UPDATE product_spec_group SET category_id = 5  WHERE id = 4  AND category_id IS NULL;
UPDATE product_spec_group SET category_id = 6  WHERE id = 5  AND category_id IS NULL;
UPDATE product_spec_group SET category_id = 7  WHERE id = 6  AND category_id IS NULL;
UPDATE product_spec_group SET category_id = 7  WHERE id = 7  AND category_id IS NULL;
UPDATE product_spec_group SET category_id = 8  WHERE id = 8  AND category_id IS NULL;
UPDATE product_spec_group SET category_id = 7  WHERE id = 9  AND category_id IS NULL;
UPDATE product_spec_group SET category_id = 7  WHERE id = 10 AND category_id IS NULL;
SELECT id, name, category_id FROM product_spec_group ORDER BY id;
