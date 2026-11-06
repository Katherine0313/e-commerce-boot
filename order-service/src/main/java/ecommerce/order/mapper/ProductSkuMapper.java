package ecommerce.order.mapper;

import ecommerce.order.entity.ProductSku;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * SKU 数据访问（下单扣减/恢复、购物车读价格与库存）。
 */
public interface ProductSkuMapper {

    ProductSku findById(@Param("id") Long id);

    /** 未指定 skuId 时（旧客户端/单规格）取该商品排序最靠前的启用 SKU */
    ProductSku findDefaultByProductId(@Param("productId") Long productId);

    /** 批量查 SKU（购物车合并算价用），按 id 集合 */
    List<ProductSku> listByIds(@Param("ids") List<Long> ids);

    /** 扣库存：stock >= quantity 条件防超卖，返回 0 表示库存不足 */
    int deductStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    /** 恢复库存 */
    int restoreStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    /** 扣减后刷新商品级库存冗余 */
    int refreshProductStock(@Param("productId") Long productId);
}
