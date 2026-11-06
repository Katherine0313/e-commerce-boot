package ecommerce.product.mapper;

import ecommerce.product.entity.ProductSkuLite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 专门给库存模块用的 product_sku Mapper。
 * 只做 3 件事：行锁读当前 stock / 直接赋值 UPDATE / 刷新 products.stock 汇总。
 * 不复用 AdminProductServiceImpl 里的 ProductSkuMapper。
 */
@Mapper
public interface StockProductSkuMapper {
    /** FOR UPDATE 行锁读取，必须和 updateStockDirect 在同一个 @Transactional 里 */
    ProductSkuLite lockById(@Param("skuId") Long skuId);

    /** 直接赋值更新（delta 已在 Java 算好） */
    void updateStockDirect(@Param("skuId") Long skuId, @Param("newStock") Integer newStock);

    /** 刷新商品级汇总冗余（products.stock = SUM(sku.stock) WHERE status = 1） */
    void refreshProductStock(@Param("productId") Long productId);
}
