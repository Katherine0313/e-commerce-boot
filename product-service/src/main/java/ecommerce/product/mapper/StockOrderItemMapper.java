package ecommerce.product.mapper;

import ecommerce.product.entity.StockOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StockOrderItemMapper {
    void insertAll(@Param("list") List<StockOrderItem> items);
    List<StockOrderItem> findByOrderId(@Param("orderId") Long orderId);
}
