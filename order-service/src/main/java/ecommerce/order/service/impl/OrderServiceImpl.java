package ecommerce.order.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.order.dto.CreateOrderDTO;
import ecommerce.order.dto.OrderItemDTO;
import ecommerce.order.entity.Order;
import ecommerce.order.entity.OrderItem;
import ecommerce.order.entity.Product;
import ecommerce.order.enums.OrderStatusEnum;
import ecommerce.order.mapper.OrderItemMapper;
import ecommerce.order.mapper.OrderMapper;
import ecommerce.order.mapper.ProductMapper;
import ecommerce.order.service.OrderService;
import ecommerce.order.vo.OrderItemVO;
import ecommerce.order.vo.OrderVO;
import ecommerce.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单 Service 实现。
 * <p>
 * 设计理念照原 Node 后端：状态机流转 + 状态自动记时间戳 + 超时关闭并发二次确认 + 库存扣减/恢复。
 */
@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    /** 模板店（杰米小店）：多租户沙箱接入前固定为 1 */
    private static final Long TEMPLATE_SHOP_ID = 1L;
    /** 待付款订单超时关闭时长（秒）：30 分钟 */
    private static final long PENDING_PAY_TIMEOUT_SECONDS = 1800;
    /** 订单号格式：ORD + 年月日时分秒 */
    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;

    public OrderServiceImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper, ProductMapper productMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
    }

    @Override
    @Transactional
    public OrderVO createOrder(CreateOrderDTO dto) {
        if (dto.getConsignee() == null || dto.getConsignee().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "收货人不能为空");
        }
        if (dto.getPhone() == null || dto.getPhone().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "手机号不能为空");
        }
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "商品明细不能为空");
        }

        Long userId = currentUserId();
        Long shopId = TEMPLATE_SHOP_ID;

        // 逐个查商品，校验库存，组装明细，算总额
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderItemDTO itemDTO : dto.getItems()) {
            if (itemDTO.getProductId() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "商品 ID 不能为空");
            }
            Product product = productMapper.findById(itemDTO.getProductId(), shopId);
            if (product == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "商品 ID " + itemDTO.getProductId() + " 不存在");
            }
            int quantity = itemDTO.getQuantity() == null ? 1 : Math.max(1, itemDTO.getQuantity());
            int stock = product.getStock() == null ? 0 : product.getStock();
            if (stock < quantity) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "商品「" + product.getName() + "」库存不足（剩余 " + stock + " 件）");
            }
            BigDecimal price = product.getSalesPrice() == null ? BigDecimal.ZERO : product.getSalesPrice();
            BigDecimal insuranceFee = itemDTO.getInsuranceFee() == null ? BigDecimal.ZERO : itemDTO.getInsuranceFee();
            BigDecimal subTotal = price.add(insuranceFee).multiply(BigDecimal.valueOf(quantity));
            orderItems.add(OrderItem.builder()
                    .shopId(shopId)
                    .productId(product.getId())
                    .productName(product.getName())
                    .image(product.getImage())
                    .salesPrice(price)
                    .quantity(quantity)
                    .subTotal(subTotal)
                    .insuranceDetail(itemDTO.getInsuranceDetail() == null ? "[]" : itemDTO.getInsuranceDetail())
                    .insuranceFee(insuranceFee)
                    .build());
            totalAmount = totalAmount.add(subTotal);
        }

        // 扣库存（乐观锁兜底防超卖）
        for (OrderItem item : orderItems) {
            if (productMapper.deductStock(item.getProductId(), item.getQuantity()) == 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "商品「" + item.getProductName() + "」库存不足");
            }
        }

        // 写订单主表（初始状态：待付款）
        Order order = Order.builder()
                .shopId(shopId)
                .userId(userId)
                .orderNo(generateOrderNo())
                .payAmount(totalAmount)
                .orderStatus(OrderStatusEnum.PENDING_PAY.getCode())
                .remark(dto.getRemark())
                .consignee(dto.getConsignee())
                .phone(dto.getPhone())
                .region(dto.getRegion())
                .detailAddress(dto.getDetailAddress())
                .shippingType("商家配送")
                .build();
        orderMapper.insert(order);

        // 写订单明细（回填 orderId）
        for (OrderItem item : orderItems) {
            item.setOrderId(order.getId());
        }
        orderItemMapper.insertBatch(orderItems);

        return getOrder(order.getId());
    }

    @Override
    public OrderVO getOrder(Long id) {
        return toVO(getOwnedOrder(id));
    }

    @Override
    public List<OrderVO> listOrders() {
        return orderMapper.listByUser(currentUserId(), TEMPLATE_SHOP_ID).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional
    public OrderVO payOrder(Long id, String payType) {
        Order order = getOwnedOrder(id);
        checkTransition(order, OrderStatusEnum.PENDING_CONFIRM);
        orderMapper.updateStatus(id, TEMPLATE_SHOP_ID, OrderStatusEnum.PENDING_CONFIRM.getCode(), payType, null, null);
        return getOrder(id);
    }

    @Override
    @Transactional
    public OrderVO confirmOrder(Long id) {
        Order order = getOwnedOrder(id);
        checkTransition(order, OrderStatusEnum.PENDING_SHIP);
        orderMapper.updateStatus(id, TEMPLATE_SHOP_ID, OrderStatusEnum.PENDING_SHIP.getCode(), null, null, null);
        return getOrder(id);
    }

    @Override
    @Transactional
    public OrderVO shipOrder(Long id, String courier, String trackingNumber) {
        Order order = getOwnedOrder(id);
        checkTransition(order, OrderStatusEnum.SHIPPED);
        orderMapper.updateStatus(id, TEMPLATE_SHOP_ID, OrderStatusEnum.SHIPPED.getCode(), null, courier, trackingNumber);
        return getOrder(id);
    }

    @Override
    @Transactional
    public OrderVO finishOrder(Long id) {
        Order order = getOwnedOrder(id);
        checkTransition(order, OrderStatusEnum.FINISHED);
        orderMapper.updateStatus(id, TEMPLATE_SHOP_ID, OrderStatusEnum.FINISHED.getCode(), null, null, null);
        return getOrder(id);
    }

    @Override
    @Transactional
    public OrderVO cancelOrder(Long id) {
        Order order = getOwnedOrder(id);
        checkTransition(order, OrderStatusEnum.CANCEL);
        restoreStock(order.getId(), TEMPLATE_SHOP_ID);
        orderMapper.updateStatus(id, TEMPLATE_SHOP_ID, OrderStatusEnum.CANCEL.getCode(), null, null, null);
        return getOrder(id);
    }

    /** 超时关闭待付款订单：每分钟扫一次，恢复库存 + 改 cancel（保留订单记录，不软删除） */
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void sweepExpiredPendingPayOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(PENDING_PAY_TIMEOUT_SECONDS);
        List<Order> expired = orderMapper.findExpiredPendingPay(OrderStatusEnum.PENDING_PAY.getCode(), threshold);
        for (Order order : expired) {
            // 二次确认仍是待付款，避免与支付并发时误取消已付款订单
            Order latest = orderMapper.findById(order.getId(), order.getShopId());
            if (latest == null || !latest.getOrderStatus().equals(OrderStatusEnum.PENDING_PAY.getCode())) {
                continue;
            }
            restoreStock(order.getId(), order.getShopId());
            // 只改状态为取消，不软删除：待付款订单是有效订单记录，超时关闭只是状态流转（pending_pay → cancel），
            // 保留下来用户才能在订单列表看到「这笔订单超时取消了」
            orderMapper.updateStatus(order.getId(), order.getShopId(), OrderStatusEnum.CANCEL.getCode(), null, null, null);
        }
        if (!expired.isEmpty()) {
            log.info("超时关闭待付款订单 {} 条", expired.size());
        }
    }

    // ==================== 内部工具 ====================

    private Order getOwnedOrder(Long id) {
        Order order = orderMapper.findById(id, TEMPLATE_SHOP_ID);
        if (order == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(currentUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权操作该订单");
        }
        return order;
    }

    private void checkTransition(Order order, OrderStatusEnum target) {
        OrderStatusEnum current = OrderStatusEnum.fromCode(order.getOrderStatus());
        if (current == null || !current.canTransitionTo(target)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "不允许从「" + (current == null ? "未知状态" : current.getDesc()) + "」变更为「" + target.getDesc() + "」");
        }
    }

    private void restoreStock(Long orderId, Long shopId) {
        List<OrderItem> items = orderItemMapper.listByOrderId(orderId, shopId);
        for (OrderItem item : items) {
            productMapper.restoreStock(item.getProductId(), item.getQuantity());
        }
    }

    private String generateOrderNo() {
        return "ORD" + LocalDateTime.now().format(ORDER_NO_FORMATTER);
    }

    private Long currentUserId() {
        return UserContext.getUserId();
    }

    private OrderVO toVO(Order order) {
        OrderStatusEnum status = OrderStatusEnum.fromCode(order.getOrderStatus());
        List<OrderItem> items = orderItemMapper.listByOrderId(order.getId(), order.getShopId());
        return OrderVO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .payAmount(order.getPayAmount())
                .payType(order.getPayType())
                .orderStatus(order.getOrderStatus())
                .orderStatusDesc(status == null ? null : status.getDesc())
                .remark(order.getRemark())
                .consignee(order.getConsignee())
                .phone(order.getPhone())
                .region(order.getRegion())
                .detailAddress(order.getDetailAddress())
                .shippingType(order.getShippingType())
                .courier(order.getCourier())
                .trackingNumber(order.getTrackingNumber())
                .signStatus(order.getSignStatus())
                .createdAt(order.getCreatedAt())
                .payAt(order.getPayAt())
                .shipAt(order.getShipAt())
                .confirmAt(order.getConfirmAt())
                .finishAt(order.getFinishAt())
                .items(items.stream().map(this::toItemVO).toList())
                .build();
    }

    private OrderItemVO toItemVO(OrderItem item) {
        return OrderItemVO.builder()
                .productId(item.getProductId())
                .productName(item.getProductName())
                .image(item.getImage())
                .salesPrice(item.getSalesPrice())
                .quantity(item.getQuantity())
                .subTotal(item.getSubTotal())
                .build();
    }
}
