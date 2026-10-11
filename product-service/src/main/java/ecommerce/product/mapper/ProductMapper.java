package ecommerce.product.mapper;

import ecommerce.product.entity.Product;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface ProductMapper {
    // 上架商品列表（listing_status=1，不含图文详情 body 与成本价 cost_price）
    List<Product> listOnSale(@Param("shopId") Long shopId);

    // 根据 id 查单个商品（含图文详情 body，仍不下发 cost_price）
    Product findById(@Param("id") Long id, @Param("shopId") Long shopId);

    // 按 id 列表查上架商品（推荐接口用，不含 body/cost_price）
    List<Product> listOnSaleByIds(@Param("ids") List<Long> ids, @Param("shopId") Long shopId);

    // 按一级分类（父类）查上架商品：商品挂在二级分类，按 parent_id 聚合到父类（不含 body/cost_price）
    List<Product> listOnSaleByCategory(@Param("categoryId") Long categoryId, @Param("shopId") Long shopId);
}