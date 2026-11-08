package ecommerce.product.mapper;

import ecommerce.product.entity.ProductSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品 SKU 数据访问。
 */
@Mapper
public interface ProductSkuMapper {
    /** 后台编辑回显：包含启用与停用行 */
    List<ProductSku> listByProductId(@Param("productId") Long productId);

    /** C 端详情：只返回启用行 */
    List<ProductSku> listEnabledByProductId(@Param("productId") Long productId);

    /** 按 id 查单个 SKU（AI 内部库存查询用，含停用行） */
    ProductSku findById(@Param("id") Long id);

    /** SKU 累计销量（共享库只读 order_items/orders；口径与订单销量排名一致：排除待付款/已取消，按已支付订单计） */
    long sumSalesBySkuId(@Param("skuId") Long skuId);

    int insert(ProductSku sku);

    int update(ProductSku sku);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int updateStatusByProductId(@Param("productId") Long productId, @Param("status") Integer status);

    /** 汇总 products.stock / sales_price / market_price 冗余字段 */
    int refreshProductAggregate(@Param("productId") Long productId, @Param("now") LocalDateTime now);
}
