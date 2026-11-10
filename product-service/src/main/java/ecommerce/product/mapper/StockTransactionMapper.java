package ecommerce.product.mapper;

import ecommerce.product.entity.StockTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StockTransactionMapper {
    void insert(StockTransaction tx);
    List<StockTransaction> pageList(@Param("skuId") Long skuId,
                                    @Param("search") String search,
                                    @Param("type") String type,
                                    @Param("reason") String reason,
                                    @Param("orderItemId") Long orderItemId,
                                    @Param("startTime") String startTime,
                                    @Param("endTime") String endTime,
                                    @Param("offset") int offset,
                                    @Param("size") int size);
    int pageCount(@Param("skuId") Long skuId,
                  @Param("search") String search,
                  @Param("type") String type,
                  @Param("reason") String reason,
                  @Param("orderItemId") Long orderItemId,
                  @Param("startTime") String startTime,
                  @Param("endTime") String endTime);

    /** 看板：按日聚合近 N 天出入库（返回 [{day, in_qty, out_qty}]） */
    List<java.util.Map<String, Object>> dailyTrend(@Param("days") int days);
    /** 看板：今日总入库 */
    int todayTotalIn();
    /** 看板：今日总出库 */
    int todayTotalOut();
    /** 看板：业务类型占比（近 N 天，返回 [{type, count, qty}]） */
    List<java.util.Map<String, Object>> typeDist(@Param("days") int days);
    /** 看板：总库存件数（product_sku.sum(stock) WHERE status=1） */
    long totalStock();
}
