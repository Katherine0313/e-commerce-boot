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
    Order findById(@Param("id") Long id);

    // 查某订单的明细（快照到售后明细用，不含软删除）
    List<OrderItem> listItems(@Param("orderId") Long orderId);

    // 查订单的单个明细（校验 orderItemId 归属 orderId + 单品快照用，不含软删除）
    OrderItem findItemById(@Param("orderItemId") Long orderItemId, @Param("orderId") Long orderId);

    // 回写订单项售后标记
    int updateItemAfterSale(@Param("orderItemId") Long orderItemId, @Param("afterSaleId") Long afterSaleId,
                            @Param("status") String status, @Param("now") java.time.LocalDateTime now);

    // 统计订单里未处于指定售后标记的订单项数量（判断整单是否全部退款）
    int countItemsNotStatus(@Param("orderId") Long orderId, @Param("status") String status);

    // 回补商品库存（退款/退货回库）
    int restoreStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    // 更新订单主状态
    int updateOrderStatus(@Param("orderId") Long orderId, @Param("status") Integer status,
                          @Param("now") java.time.LocalDateTime now);
}
