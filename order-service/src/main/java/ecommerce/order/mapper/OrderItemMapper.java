package ecommerce.order.mapper;

import ecommerce.order.entity.OrderItem;
import ecommerce.order.vo.SalesRankingItemVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单项 Mapper（order_items 表）。
 */
public interface OrderItemMapper {

    // 批量插入订单明细
    int insertBatch(@Param("items") List<OrderItem> items);

    // 查某订单的明细（不含软删除）
    List<OrderItem> listByOrderId(@Param("orderId") Long orderId, @Param("shopId") Long shopId);

    // 销量排名：某区间已支付订单按商品汇总销量（pay_at 归属，排除待付款/已取消，退款暂不扣减）
    List<SalesRankingItemVO> sumSalesRanking(@Param("shopId") Long shopId,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end,
                                             @Param("limit") int limit);
}
