-- 商品图 URL 追加缓存版本号（解决「客户端一直看到旧占位图」）
--
-- 背景（2026-11-02）：
--   2026-10-31 09:03~09:04，服务器上 136 个商品图文件被**原地覆盖**（真实照片替换了种子占位图），
--   但 106 个商品的 image 值形如 /image/upload/N.webp，**文件名没变 → URL 没变**。
--   而 /image/** 的响应头是 `Cache-Control: public, immutable, max-age=31536000`
--   （common/src/main/java/ecommerce/configuration/WebMvcConfig.java:84），
--   immutable = 客户端承诺「内容永不变化」，一年内不再回源校验。
--   → 10-31 之前下载过这些 URL 的客户端（微信小程序按微信账号分区缓存）就一直拿旧占位图。
--
-- 修法：给 image 追加 ?v=<换图日期>，URL 变化 → 所有端强制重新下载。
--   机械表那批（106~136）当时已经这么做过（?v=20261030），本脚本补齐剩下的。
--
-- 幂等：只改「没有 ?v= 的行」，重跑不会重复追加。
-- 本地与生产必须跑同一份（改本地库要同步生产）。

-- 改前自查：应该是 106 行
-- SELECT COUNT(*) FROM products WHERE image LIKE '/image/%' AND image NOT LIKE '%?v=%';

UPDATE products
SET image = CONCAT(image, '?v=20261031')
WHERE image LIKE '/image/%'
  AND image NOT LIKE '%?v=%';

-- 改后自查：应该是 0 行
-- SELECT COUNT(*) FROM products WHERE image LIKE '/image/%' AND image NOT LIKE '%?v=%';
-- 抽样看效果
-- SELECT id, image FROM products WHERE id IN (1, 85, 105, 128, 136);
