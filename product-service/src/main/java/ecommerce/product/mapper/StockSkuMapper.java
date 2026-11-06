package ecommerce.product.mapper;

import ecommerce.product.vo.StockSkuVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 给库存模块 StockSkuController 用的 SKU 查询 Mapper。
 */
@Mapper
public interface StockSkuMapper {

    /** SKU 库存列表，支持商品名/SKU编码模糊搜索、低库存过滤 */
    List<StockSkuVO> pageList(@Param("search") String search,
                              @Param("lowStockOnly") boolean lowStockOnly,
                              @Param("offset") int offset,
                              @Param("size") int size);
    int pageCount(@Param("search") String search,
                  @Param("lowStockOnly") boolean lowStockOnly);

    /** 看板：低库存 TOP N SKU */
    List<StockSkuVO> lowStockTopN(@Param("limit") int limit);

    /** 看板：低库存 SKU 总数 */
    int countLowStock();

    /** 看板：SKU 总数 */
    int totalCount();

    /** 更新预警阈值 */
    void updateAlert(@Param("skuId") Long skuId,
                     @Param("minStock") Integer minStock,
                     @Param("maxStock") Integer maxStock);
}
