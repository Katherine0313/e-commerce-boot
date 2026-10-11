package ecommerce.order.mapper;

import ecommerce.order.entity.OrderItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单项 Mapper（order_items 表）。
 */
public interface OrderItemMapper {

    // 批量插入订单明细
    int insertBatch(@Param("items") List<OrderItem> items);

    // 查某订单的明细（不含软删除）
    List<OrderItem> listByOrderId(@Param("orderId") Long orderId, @Param("shopId") Long shopId);
}
