package ecommerce.order.service;

import ecommerce.order.dto.CreateOrderDTO;
import ecommerce.order.vo.OrderVO;

import java.util.List;

/**
 * 订单 Service。
 * <p>
 * 当前登录用户从 UserContext.getUserId() 取，店铺固定模板店（shopId=1）。
 * 状态机流转规则见 OrderStatusEnum。
 */
public interface OrderService {

    /** 下单：查商品 → 校验库存 → 扣库存 → 写订单+明细（初始状态待付款） */
    OrderVO createOrder(CreateOrderDTO dto);

    /** 订单详情 */
    OrderVO getOrder(Long id);

    /** 当前用户订单列表 */
    List<OrderVO> listOrders();

    /** 支付：待付款 → 待确认 */
    OrderVO payOrder(Long id, String payType);

    /** 确认收款：待确认 → 待发货 */
    OrderVO confirmOrder(Long id);

    /** 发货：待发货 → 已发货 */
    OrderVO shipOrder(Long id, String courier, String trackingNumber);

    /** 完成：已发货 → 已完成 */
    OrderVO finishOrder(Long id);

    /** 取消：恢复库存 + 状态改取消 */
    OrderVO cancelOrder(Long id);

    /** 超时关闭待付款订单（定时任务调用） */
    void sweepExpiredPendingPayOrders();
}
