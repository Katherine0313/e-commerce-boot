-- =============================================================================
-- 阶段一：分类规格模板 + 存量 weight/volume 并入 specs
-- 执行时机：新版 product-service 上线【前】；旧版本仍可正常运行
-- 可重复执行：加列前检查列是否存在；数据迁移跳过已存在的同名规格项
-- =============================================================================

-- 1.1 categories 增加 spec_template 列
SET @has_spec_template := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'categories'
    AND COLUMN_NAME = 'spec_template'
);
SET @ddl := IF(@has_spec_template = 0,
  'ALTER TABLE categories ADD COLUMN spec_template TEXT NULL COMMENT ''规格模板字段名 JSON 数组，数组顺序即商品表单展示顺序'' AFTER image',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1.2 叶子分类预置重量/体积模板（已有模板不覆盖，父分类不配置）
UPDATE categories c
SET c.spec_template = '["重量(kg)","体积(m³)"]'
WHERE c.spec_template IS NULL
  AND c.id NOT IN (
    SELECT id FROM (
      SELECT DISTINCT parent_id AS id FROM categories WHERE parent_id IS NOT NULL
    ) t
  );

-- 1.3 存量商品 weight 并入 specs（已是同名规格项则跳过；specs 非法 JSON 的行不动）
UPDATE products
SET specs = CASE
  WHEN specs IS NULL OR specs = '' THEN
    JSON_ARRAY(JSON_OBJECT(
      'name', '重量(kg)',
      'value', TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(weight AS CHAR)))
    ))
  ELSE
    JSON_ARRAY_APPEND(
      specs,
      '$',
      JSON_OBJECT(
        'name', '重量(kg)',
        'value', TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(weight AS CHAR)))
      )
    )
END
WHERE weight IS NOT NULL
  AND (specs IS NULL OR specs = '' OR (JSON_VALID(specs) AND JSON_TYPE(specs) = 'ARRAY'))
  AND (specs IS NULL OR specs = '' OR JSON_SEARCH(specs, 'one', '重量(kg)', NULL, '$[*].name') IS NULL);

-- 1.4 存量商品 volume 并入 specs
UPDATE products
SET specs = CASE
  WHEN specs IS NULL OR specs = '' THEN
    JSON_ARRAY(JSON_OBJECT(
      'name', '体积(m³)',
      'value', TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(volume AS CHAR)))
    ))
  ELSE
    JSON_ARRAY_APPEND(
      specs,
      '$',
      JSON_OBJECT(
        'name', '体积(m³)',
        'value', TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(volume AS CHAR)))
      )
    )
END
WHERE volume IS NOT NULL
  AND (specs IS NULL OR specs = '' OR (JSON_VALID(specs) AND JSON_TYPE(specs) = 'ARRAY'))
  AND (specs IS NULL OR specs = '' OR JSON_SEARCH(specs, 'one', '体积(m³)', NULL, '$[*].name') IS NULL);

-- 1.5 自检
SELECT COUNT(*) AS column_ready FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'categories' AND COLUMN_NAME = 'spec_template';

SELECT COUNT(*) AS templated_categories FROM categories WHERE spec_template IS NOT NULL;

SELECT COUNT(*) AS unmerged_weight FROM products
WHERE weight IS NOT NULL
  AND (specs IS NULL OR specs = ''
       OR (JSON_VALID(specs) AND JSON_TYPE(specs) = 'ARRAY'
           AND JSON_SEARCH(specs, 'one', '重量(kg)', NULL, '$[*].name') IS NULL));

SELECT COUNT(*) AS unmerged_volume FROM products
WHERE volume IS NOT NULL
  AND (specs IS NULL OR specs = ''
       OR (JSON_VALID(specs) AND JSON_TYPE(specs) = 'ARRAY'
           AND JSON_SEARCH(specs, 'one', '体积(m³)', NULL, '$[*].name') IS NULL));
