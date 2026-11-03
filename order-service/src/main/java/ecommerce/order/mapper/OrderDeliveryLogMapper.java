package ecommerce.order.mapper;

import ecommerce.order.entity.OrderDeliveryLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 发货操作流水 Mapper（order_delivery_log 表）。
 */
@Mapper
public interface OrderDeliveryLogMapper {

    /** 写入一条发货流水 */
    int insert(OrderDeliveryLog log);

    /** 某订单的全部发货流水（按时间正序） */
    List<OrderDeliveryLog> listByOrderId(@Param("orderId") Long orderId);
}
