package ecommerce.product.mapper;

import ecommerce.product.entity.StockOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StockOrderMapper {
    void insert(StockOrder order);
    StockOrder findById(@Param("id") Long id);
    StockOrder findByOrderNo(@Param("orderNo") String orderNo);
    /** 刷新 total_qty（SUM of items.qty） */
    void refreshTotal(@Param("id") Long id);
    void updateStatus(@Param("id") Long id, @Param("status") int status);
}
