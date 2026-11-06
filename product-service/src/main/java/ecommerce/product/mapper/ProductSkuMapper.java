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

    int insert(ProductSku sku);

    int update(ProductSku sku);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int updateStatusByProductId(@Param("productId") Long productId, @Param("status") Integer status);

    /** 汇总 products.stock / sales_price / market_price 冗余字段 */
    int refreshProductAggregate(@Param("productId") Long productId, @Param("now") LocalDateTime now);
}
