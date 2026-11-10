package ecommerce.product.mapper;

import ecommerce.product.entity.StockOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StockOrderMapper {
    void insert(StockOrder order);
    StockOrder findById(@Param("id") Long id);
    StockOrder findByOrderNo(@Param("orderNo") String orderNo);
    /** 按关联单号 + 单据类型查（订单发货出库单幂等用） */
    StockOrder findByRelatedNoAndType(@Param("relatedNo") String relatedNo, @Param("type") String type);
    /** 刷新 total_qty（SUM of items.qty） */
    void refreshTotal(@Param("id") Long id);
    void updateStatus(@Param("id") Long id, @Param("status") int status);
}
