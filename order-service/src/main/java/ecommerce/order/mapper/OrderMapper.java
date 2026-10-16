package ecommerce.order.mapper;

import ecommerce.order.entity.Order;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单 Mapper（orders 表）。
 */
public interface OrderMapper {

    // 创建订单（回填自增 id）
    int insert(Order order);

    // 查单个订单（不含软删除）
    Order findById(@Param("id") Long id, @Param("shopId") Long shopId);

    // 查某用户订单列表（按创建时间倒序；keyword 非空时按订单号/收货人/商品名模糊搜索）
    List<Order> listByUser(@Param("userId") Long userId, @Param("shopId") Long shopId, @Param("keyword") String keyword);

    /**
     * 状态流转：更新状态 + 自动记录对应时间戳。
     * status 用 OrderStatusEnum 的数字 code，按目标状态更新对应时间字段（见 XML 动态 SQL）。
     */
    int updateStatus(@Param("id") Long id, @Param("shopId") Long shopId,
                     @Param("status") Integer status, @Param("payType") String payType,
                     @Param("courier") String courier, @Param("trackingNumber") String trackingNumber);

    // 软删除（is_deleted=1）
    int delete(@Param("id") Long id, @Param("shopId") Long shopId);

    // 查超时的待付款订单（定时关闭用）：status 且 created_at <= threshold 且未删除
    List<Order> findExpiredPendingPay(@Param("status") Integer status, @Param("threshold") LocalDateTime threshold);
}
