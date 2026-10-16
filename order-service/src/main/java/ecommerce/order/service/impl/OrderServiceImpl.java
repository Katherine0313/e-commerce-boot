package ecommerce.order.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.order.dto.CreateOrderDTO;
import ecommerce.order.dto.OrderItemDTO;
import ecommerce.order.entity.Order;
import ecommerce.order.entity.OrderItem;
import ecommerce.order.entity.Product;
import ecommerce.order.enums.OrderStatusEnum;
import ecommerce.order.enums.PayTypeEnum;
import ecommerce.order.mapper.OrderItemMapper;
import ecommerce.order.mapper.OrderMapper;
import ecommerce.order.mapper.ProductMapper;
import ecommerce.order.service.OrderService;
import ecommerce.order.vo.OrderItemVO;
import ecommerce.order.vo.OrderVO;
import ecommerce.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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
    /** 订单号格式：ORD + 年月日时分秒毫秒（再加 4 位随机数防同毫秒撞唯一索引） */
    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    /** 下单幂等：Redis key 前缀，key = 前缀 + userId + ":" + idempotencyKey */
    private static final String IDEMPOTENCY_KEY_PREFIX = "order:idempotency:";
    /** 幂等占位值：表示该 key 对应的下单请求正在处理中 */
    private static final String IDEMPOTENCY_PENDING = "PENDING";
    /** 幂等键存活时长：24 小时，覆盖待付款超时（30 分钟）及用户中途退出重进 */
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public OrderServiceImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper, ProductMapper productMapper,
                            StringRedisTemplate stringRedisTemplate) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    @Transactional
    public OrderVO createOrder(CreateOrderDTO dto) {
        if (dto.getConsignee() == null || dto.getConsignee().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "收货人不能为空");
        }
        if (dto.getPhone() == null || !dto.getPhone().trim().matches("\\d{11}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "手机号格式不正确");
        }
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "商品明细不能为空");
        }
        if (dto.getIdempotencyKey() == null || dto.getIdempotencyKey().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "缺少幂等键");
        }

        Long userId = currentUserId();
        String redisKey = IDEMPOTENCY_KEY_PREFIX + userId + ":" + dto.getIdempotencyKey();

        // 命中已有结果：处理中直接提示，已成功则返回原订单（getOrder 内部校验归属）
        String existing = safeGet(redisKey);
        if (existing != null) {
            if (IDEMPOTENCY_PENDING.equals(existing)) {
                throw new BusinessException(HttpStatus.CONFLICT, "订单处理中，请稍后查看订单列表");
            }
            return getOrder(Long.parseLong(existing));
        }

        // SETNX 占位：并发下同一 key 只有第一个请求能占位成功，防止重复下单
        if (!safeSetIfAbsent(redisKey, IDEMPOTENCY_PENDING, IDEMPOTENCY_TTL)) {
            throw new BusinessException(HttpStatus.CONFLICT, "订单处理中，请稍后查看订单列表");
        }

        try {
            OrderVO vo = doCreateOrder(dto, userId);
            // 占位改写为订单 ID，后续相同 key 直接返回该订单
            safeSet(redisKey, String.valueOf(vo.getId()), IDEMPOTENCY_TTL);
            return vo;
        } catch (RuntimeException e) {
            // 下单失败删除占位，允许用户下次重试重新下单
            safeDelete(redisKey);
            throw e;
        }
    }

    /** 原有下单逻辑：查商品 → 校验库存 → 扣库存 → 写订单。幂等占位/结果写回由外层 createOrder 负责。 */
    private OrderVO doCreateOrder(CreateOrderDTO dto, Long userId) {
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
            if (productMapper.deductStock(item.getProductId(), item.getQuantity(), shopId) == 0) {
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
                // createdAt 用 JVM 的 LocalDateTime.now()，与 orderNo 同一时区；不用 MySQL NOW()，
                // 否则数据库时区(UTC)和 JVM(UTC+8) 差 8 小时，前端倒计时会误判「超时关闭」
                .createdAt(LocalDateTime.now())
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
    public List<OrderVO> listOrders(String keyword) {
        return orderMapper.listByUser(currentUserId(), TEMPLATE_SHOP_ID, keyword).stream()
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

    @Override
    @Transactional
    public void deleteOrder(Long id) {
        // 软删：只校验存在 + 归属（不校验状态，任何状态的订单都能删）；is_deleted=1 后列表不再返回
        getOwnedOrder(id);
        orderMapper.delete(id, TEMPLATE_SHOP_ID);
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
            productMapper.restoreStock(item.getProductId(), item.getQuantity(), shopId);
        }
    }

    private String generateOrderNo() {
        // order_no 有唯一索引，秒级精度同秒会撞；毫秒 + 4 位随机数保证并发下不重复
        return "ORD" + LocalDateTime.now().format(ORDER_NO_FORMATTER)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private Long currentUserId() {
        return UserContext.getUserId();
    }

    // ==================== 幂等 Redis 降级包装 ====================
    // Redis 异常时一律降级（记日志、返回兜底值），保证 Redis 宕机不影响下单

    private String safeGet(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("幂等键读取失败，降级跳过幂等校验: key={}", key, e);
            return null;
        }
    }

    private boolean safeSetIfAbsent(String key, String value, Duration ttl) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, value, ttl));
        } catch (Exception e) {
            log.warn("幂等键占位失败，降级视为占位成功: key={}", key, e);
            return true;
        }
    }

    private void safeSet(String key, String value, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("幂等键写结果失败，忽略: key={}", key, e);
        }
    }

    private void safeDelete(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("幂等键删除失败，忽略: key={}", key, e);
        }
    }

    private OrderVO toVO(Order order) {
        OrderStatusEnum status = OrderStatusEnum.fromCode(order.getOrderStatus());
        PayTypeEnum payType = PayTypeEnum.fromCode(order.getPayType());
        List<OrderItem> items = orderItemMapper.listByOrderId(order.getId(), order.getShopId());
        return OrderVO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .payAmount(order.getPayAmount())
                .payType(order.getPayType())
                .payTypeDesc(payType == null ? null : payType.getDesc())
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