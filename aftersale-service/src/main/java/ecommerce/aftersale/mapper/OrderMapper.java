package ecommerce.aftersale.mapper;

import ecommerce.aftersale.entity.Order;
import ecommerce.aftersale.entity.OrderItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单 Mapper（aftersale 域内只读，查 orders / order_items 表）。
 * <p>
 * 售后与订单共用同一数据库，这里只提供售后申请所需的订单校验与明细快照查询。
 */
public interface OrderMapper {

    // 查单个订单（校验归属 + 状态用，不含软删除）
    Order findById(@Param("id") Long id, @Param("shopId") Long shopId);

    // 查某订单的明细（快照到售后明细用，不含软删除）
    List<OrderItem> listItems(@Param("orderId") Long orderId, @Param("shopId") Long shopId);
}
