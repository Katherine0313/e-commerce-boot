-- =====================================================================
-- 08：修正种子商品脏数据（幂等，可重复执行）
-- 76：05-fix-titles 把旧的 sub_title「c」拼进了 title，去掉这段
-- 85：生成器没识别出「MX Master 3S」是鼠标，name/title/specs 落成了手机壳模板的兜底值
-- sub_title 由 07-fix-subtitles.sql 覆盖，这里不处理
-- =====================================================================
SET NAMES utf8mb4;

UPDATE products
SET title = '尼康Z6III 数码相机 APS-C CMOS 2420万 4K 120p 官方正品 现货速发'
WHERE id = 76;

UPDATE products
SET name  = '罗技MX Master 3S 无线鼠标',
    title = '罗技MX Master 3S 无线鼠标 静音按键 MagSpeed电磁滚轮 多设备切换 8000DPI USB-C 70天 官方正品 现货速发',
    specs = '[{"name":"连接方式","value":"2.4G+蓝牙"},{"name":"DPI","value":"8000DPI"},{"name":"滚轮","value":"MagSpeed电磁滚轮"},{"name":"续航","value":"70天"},{"name":"接口","value":"USB-C"}]'
WHERE id = 85;

SELECT id, name, title, specs FROM products WHERE id IN (76, 85);
