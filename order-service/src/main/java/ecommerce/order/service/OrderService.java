package ecommerce.order.service;

import ecommerce.common.promotion.QuoteResult;
import ecommerce.order.dto.CreateOrderDTO;
import ecommerce.order.dto.OrderItemDTO;
import ecommerce.order.vo.LogisticsVO;
import ecommerce.order.vo.OrderVO;

import java.util.List;

/**
 * 订单 Service。
 * <p>
 * 当前登录用户从 UserContext.getUserId() 取（单店模式）。
 * 状态机流转规则见 OrderStatusEnum。
 */
public interface OrderService {

    /** 下单：查商品 → 校验库存 → 扣库存 → 写订单+明细（初始状态待付款） */
    OrderVO createOrder(CreateOrderDTO dto);

    /** 确认页报价：按商品明细实时计算直减/满减/优惠券（不落库，下单时会重新计算） */
    QuoteResult quote(List<OrderItemDTO> items, Long couponId);

    /** 订单详情 */
    OrderVO getOrder(Long id);

    /** 当前用户订单列表（keyword 为空查全部，非空按订单号/收货人/商品名模糊搜索） */
    List<OrderVO> listOrders(String keyword);

    /** 支付：待付款 → 待发货（支付成功即进入待发货，无人工确认收款环节） */
    OrderVO payOrder(Long id, String payType);

    /** 发货：待发货 → 已发货 */
    OrderVO shipOrder(Long id, String courier, String trackingNumber);

    /** 完成：已发货 → 已完成 */
    OrderVO finishOrder(Long id);

    /** 取消：恢复库存 + 状态改取消 */
    OrderVO cancelOrder(Long id);

    /** 删除订单（软删：is_deleted=1，不物理删除） */
    void deleteOrder(Long id);

    /** 物流轨迹（假数据，参考旧 Node getLogistics） */
    LogisticsVO getLogistics(Long id);

    /** 超时关闭待付款订单（定时任务调用） */
    void sweepExpiredPendingPayOrders();

    /** 待付款订单订阅消息提醒（定时任务调用） */
    void sweepUnpaidNotify();

    /** 发货 7 天后自动确认收货（定时任务调用） */
    void sweepAutoConfirmOrders();
}
