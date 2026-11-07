package ecommerce.order.service.impl;

import ecommerce.common.PageVO;
import ecommerce.common.insurance.InsurancePolicy;
import ecommerce.common.promotion.QuoteItemInput;
import ecommerce.common.promotion.QuoteItemResult;
import ecommerce.common.promotion.QuoteResult;
import ecommerce.exception.BusinessException;
import ecommerce.order.client.StockInternalClient;
import ecommerce.order.config.WeChatSubscribeProperties;
import ecommerce.order.dto.CreateOrderDTO;
import ecommerce.order.dto.OrderItemDTO;
import ecommerce.order.entity.Order;
import ecommerce.order.entity.OrderItem;
import ecommerce.order.entity.Product;
import ecommerce.order.entity.ProductSku;
import ecommerce.order.enums.OrderStatusEnum;
import ecommerce.order.enums.PayTypeEnum;
import ecommerce.order.mapper.AfterSaleRefundMapper;
import ecommerce.order.mapper.CartMapper;
import ecommerce.order.mapper.CouponMapper;
import ecommerce.order.mapper.OrderItemMapper;
import ecommerce.order.mapper.OrderMapper;
import ecommerce.order.mapper.ProductMapper;
import ecommerce.order.mapper.ProductReviewMapper;
import ecommerce.order.mapper.ProductSkuMapper;
import ecommerce.order.mapper.UserMapper;
import ecommerce.order.service.CouponPricingService;
import ecommerce.order.service.FinanceReceiptService;
import ecommerce.order.service.OrderService;
import ecommerce.order.service.PromotionPricingService;
import ecommerce.order.support.InsuranceReviewer;
import ecommerce.order.support.LogisticsBuilder;
import ecommerce.order.vo.LogisticsTraceVO;
import ecommerce.order.vo.LogisticsVO;
import ecommerce.order.vo.OrderCountsVO;
import ecommerce.order.vo.OrderItemVO;
import ecommerce.order.vo.OrderVO;
import ecommerce.order.wechat.WeChatSubscribeMessageClient;
import ecommerce.order.wechat.WeChatSubscribeMessageClient.SendResult;
import ecommerce.order.ws.WebSocketSessionRegistry;
import ecommerce.task.MonitoredTask;
import ecommerce.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 订单 Service 实现。
 * <p>
 * 设计理念照原 Node 后端：状态机流转 + 状态自动记时间戳 + 超时关闭并发二次确认 + 库存扣减/恢复。
 */
@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    /** 待付款订单超时关闭时长（秒）：30 分钟 */
    private static final long PENDING_PAY_TIMEOUT_SECONDS = 1800;
    /** 已发货订单超时自动确认收货时长（天）：发货后 7 天自动变已完成，无论用户是否主动签收 */
    private static final long AUTO_CONFIRM_DAYS = 7;
    /** 订单号格式：ORD + 年月日时分秒毫秒（再加 4 位随机数防同毫秒撞唯一索引） */
    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    /** 售后单号格式：AS + 年月日时分秒（照 aftersale-service 的 generateAfterSaleNo） */
    private static final DateTimeFormatter AFTER_SALE_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** 下单幂等：Redis key 前缀，key = 前缀 + userId + ":" + idempotencyKey */
    private static final String IDEMPOTENCY_KEY_PREFIX = "order:idempotency:";
    /** 幂等占位值：表示该 key 对应的下单请求正在处理中 */
    private static final String IDEMPOTENCY_PENDING = "PENDING";
    /** 幂等键存活时长：24 小时，覆盖待付款超时（30 分钟）及用户中途退出重进 */
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final CartMapper cartMapper;
    private final ProductReviewMapper productReviewMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final AfterSaleRefundMapper afterSaleRefundMapper;
    private final UserMapper userMapper;
    private final WeChatSubscribeMessageClient weChatSubscribeMessageClient;
    private final WeChatSubscribeProperties subscribeProperties;
    private final PromotionPricingService promotionPricingService;
    private final CouponPricingService couponPricingService;
    private final CouponMapper couponMapper;
    private final LogisticsBuilder logisticsBuilder;
    private final FinanceReceiptService financeReceiptService;
    private final StockInternalClient stockClient;

    public OrderServiceImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper, ProductMapper productMapper,
                            ProductSkuMapper skuMapper,
                            CartMapper cartMapper, ProductReviewMapper productReviewMapper,
                            StringRedisTemplate stringRedisTemplate,
                            ObjectMapper objectMapper, WebSocketSessionRegistry webSocketSessionRegistry,
                            AfterSaleRefundMapper afterSaleRefundMapper, UserMapper userMapper,
                            WeChatSubscribeMessageClient weChatSubscribeMessageClient,
                            WeChatSubscribeProperties subscribeProperties,
                            PromotionPricingService promotionPricingService,
                            CouponPricingService couponPricingService,
                            CouponMapper couponMapper,
                            LogisticsBuilder logisticsBuilder,
                            FinanceReceiptService financeReceiptService,
                            StockInternalClient stockClient) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.cartMapper = cartMapper;
        this.productReviewMapper = productReviewMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
        this.afterSaleRefundMapper = afterSaleRefundMapper;
        this.userMapper = userMapper;
        this.weChatSubscribeMessageClient = weChatSubscribeMessageClient;
        this.subscribeProperties = subscribeProperties;
        this.promotionPricingService = promotionPricingService;
        this.couponPricingService = couponPricingService;
        this.couponMapper = couponMapper;
        this.logisticsBuilder = logisticsBuilder;
        this.financeReceiptService = financeReceiptService;
        this.stockClient = stockClient;
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

    /** 原有下单逻辑：查商品 → 校验库存 → 促销算价 → 扣库存 → 写订单。幂等占位/结果写回由外层 createOrder 负责。 */
    private OrderVO doCreateOrder(CreateOrderDTO dto, Long userId) {
        // 逐个查商品 + SKU，校验库存，组装算价入参（价格只用后端查询结果，不信任前端）
        List<Product> products = new ArrayList<>();
        List<ProductSku> skus = new ArrayList<>();
        List<QuoteItemInput> quoteInputs = new ArrayList<>();
        for (OrderItemDTO itemDTO : dto.getItems()) {
            if (itemDTO.getProductId() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "商品 ID 不能为空");
            }
            Product product = productMapper.findById(itemDTO.getProductId());
            if (product == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "商品 ID " + itemDTO.getProductId() + " 不存在");
            }
            ProductSku sku = resolveSku(itemDTO.getProductId(), itemDTO.getSkuId());
            int quantity = itemDTO.getQuantity() == null ? 1 : Math.max(1, itemDTO.getQuantity());
            int stock = sku != null && sku.getStock() != null
                    ? sku.getStock()
                    : (product.getStock() == null ? 0 : product.getStock());
            if (stock < quantity) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "商品「" + product.getName() + "」库存不足（剩余 " + stock + " 件）");
            }
            BigDecimal price = sku != null && sku.getPrice() != null
                    ? sku.getPrice()
                    : (product.getSalesPrice() == null ? BigDecimal.ZERO : product.getSalesPrice());
            products.add(product);
            skus.add(sku);
            quoteInputs.add(QuoteItemInput.builder()
                    .productId(product.getId())
                    .quantity(quantity)
                    .originalPrice(price)
                    .build());
        }

        // 促销算价：直减改商品价、满减改商品小计，均由服务端计算
        QuoteResult quote = promotionPricingService.quote(quoteInputs, userId);
        // 优惠券：先按当前状态校验并算优惠，再原子锁定（并发下只有一笔订单能锁定成功）
        if (dto.getCouponId() != null) {
            quote = couponPricingService.applyCoupon(quote, quoteInputs, dto.getCouponId(), userId);
            if (couponMapper.lockCoupon(dto.getCouponId(), userId, LocalDateTime.now()) == 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "优惠券已被使用或不可用");
            }
        }
        List<QuoteItemResult> quotedItems = quote.getItems();

        // 组装订单明细：活动价 + 原价 + 优惠快照；保险不参与促销
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal originalTotal = BigDecimal.ZERO;
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            ProductSku sku = skus.get(i);
            QuoteItemResult quoted = quotedItems.get(i);
            int quantity = quoted.getQuantity();
            // 增值保障：不采信前端传来的明细和费用，按后端规则复核（配件类、低价商品不卖保障）
            InsurancePolicy.Result reviewed = InsuranceReviewer.review(
                    product.getCategoryId(), quoted.getFinalPrice(),
                    dto.getItems().get(i).getInsuranceDetail(), objectMapper);
            BigDecimal insuranceFee = reviewed.fee();
            BigDecimal insuranceTotal = insuranceFee.multiply(BigDecimal.valueOf(quantity));
            BigDecimal subTotal = quoted.getSubTotal().add(insuranceTotal);
            orderItems.add(OrderItem.builder()
                    .productId(product.getId())
                    .skuId(sku != null ? sku.getId() : null)
                    .skuCode(sku != null ? sku.getSkuCode() : null)
                    .specJson(sku != null ? sku.getSpecJson() : null)
                    .productName(product.getName())
                    .image(product.getImage())
                    .salesPrice(quoted.getFinalPrice())
                    .originalPrice(quoted.getOriginalPrice())
                    .quantity(quantity)
                    .subTotal(subTotal)
                    .discountAmount(quoted.getDiscountAmount())
                    .insuranceDetail(InsuranceReviewer.toJson(reviewed.options(), objectMapper))
                    .insuranceFee(insuranceFee)
                    .build());
            totalAmount = totalAmount.add(subTotal);
            originalTotal = originalTotal.add(quoted.getOriginalSubTotal().add(insuranceTotal));
        }

        // 扣库存：委托 product-service /internal/stock-write（FOR UPDATE + 乐观锁在产品侧保证原子）。
        // 调用失败抛异常 → 整个 @Transactional 回滚 → 订单不会创建，库存不会扣，闭环。
        for (int i = 0; i < orderItems.size(); i++) {
            OrderItem item = orderItems.get(i);
            ProductSku sku = skus.get(i);
            if (sku != null) {
                stockClient.write(sku.getId(), sku.getSkuCode(), "OUT", "销售出库", item.getQuantity(), null);
            } else if (productMapper.deductStock(item.getProductId(), item.getQuantity()) == 0) {
                // 无 SKU 的旧数据兜底：保留 product 级扣减（SKU 改造后这条不应该走到，但保险起见）
                throw new BusinessException(HttpStatus.BAD_REQUEST, "商品「" + item.getProductName() + "」库存不足");
            }
        }

        // 写订单主表（初始状态：待付款）；同时记录活动前金额与总优惠，供展示/退款口径使用
        Order order = Order.builder()
                .userId(userId)
                .orderNo(generateOrderNo())
                .payAmount(totalAmount)
                .originalAmount(originalTotal)
                .discountAmount(quote.getDiscountAmount())
                .couponId(quote.getCouponId())
                .couponName(quote.getCouponName())
                .couponAmount(quote.getCouponDiscount() == null ? BigDecimal.ZERO : quote.getCouponDiscount())
                .orderStatus(OrderStatusEnum.PENDING_PAY.getCode())
                // 用户点「提交订单」时同意订阅，才允许后续推送未支付提醒
                .isSubscribeUnpaid(Boolean.TRUE.equals(dto.getSubscribeUnpaid()) ? 1 : 0)
                .unpaidNotifySent(0)
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

        // 券锁定回填订单 id（取消/超时/退款时按 order_id 找回释放）
        if (dto.getCouponId() != null) {
            couponMapper.bindOrder(dto.getCouponId(), order.getId());
        }

        // 写促销使用记录：后台按活动统计应收/支付单数/优惠金额
        promotionPricingService.recordUsage(quote, order.getId(), order.getOrderNo(), userId);

        // 下单成功：从购物车移除本次已下单的行（与写订单同一事务，保证一致）
        // 多规格按 skuId 精确删除，避免把同商品其它规格误删；无 SKU 的旧数据再按商品删除
        List<Long> orderedSkuIds = orderItems.stream()
                .map(OrderItem::getSkuId).filter(java.util.Objects::nonNull).distinct().toList();
        if (!orderedSkuIds.isEmpty()) {
            cartMapper.deleteBySkus(userId, orderedSkuIds);
        }
        List<Long> orderedProductIds = orderItems.stream()
                .filter(item -> item.getSkuId() == null)
                .map(OrderItem::getProductId).distinct().toList();
        if (!orderedProductIds.isEmpty()) {
            cartMapper.deleteByProducts(userId, orderedProductIds);
        }
        return getOrder(order.getId());
    }

    @Override
    public QuoteResult quote(List<OrderItemDTO> items, Long couponId) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "商品明细不能为空");
        }
        List<QuoteItemInput> inputs = new ArrayList<>();
        for (OrderItemDTO item : items) {
            if (item == null || item.getProductId() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "商品 ID 不能为空");
            }
            Product product = productMapper.findById(item.getProductId());
            if (product == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "商品 ID " + item.getProductId() + " 不存在");
            }
            ProductSku sku = resolveSku(item.getProductId(), item.getSkuId());
            BigDecimal price = sku != null && sku.getPrice() != null
                    ? sku.getPrice()
                    : (product.getSalesPrice() == null ? BigDecimal.ZERO : product.getSalesPrice());
            inputs.add(QuoteItemInput.builder()
                    .productId(product.getId())
                    .quantity(item.getQuantity() == null ? 1 : Math.max(1, item.getQuantity()))
                    .originalPrice(price)
                    .build());
        }
        // 确认页只展示，下单时会按最新活动重新计算，前端价格不作为成交依据
        Long userId = currentUserId();
        QuoteResult quote = promotionPricingService.quote(inputs, userId);
        return couponPricingService.applyCoupon(quote, inputs, couponId, userId);
    }

    @Override
    public OrderVO getOrder(Long id) {
        return toVO(getOwnedOrder(id));
    }

    @Override
    public List<OrderVO> listOrders(String keyword) {
        return orderMapper.listByUser(currentUserId(), keyword).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public PageVO<OrderVO> pageOrders(String keyword, Integer status, int page, int pageSize) {
        Long userId = currentUserId();
        int currentPage = Math.max(1, page);
        int size = pageSize <= 0 ? 10 : Math.min(pageSize, 50);
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        long total = orderMapper.countByUser(userId, kw, status);
        List<OrderVO> list;
        if (total == 0) {
            list = List.of();
        } else {
            List<Order> orders = orderMapper.listByUserPage(userId, kw, status, (currentPage - 1) * size, size);
            // 批量装配：明细、评价、快递字典均按本次分页一次性查回，避免逐单 N+1
            Map<Long, List<OrderItem>> itemsByOrder = orders.isEmpty()
                    ? new HashMap<>()
                    : orderItemMapper.listByOrderIds(orders.stream().map(Order::getId).toList()).stream()
                            .collect(Collectors.groupingBy(OrderItem::getOrderId));
            List<Long> finishedOrderIds = orders.stream()
                    .filter(o -> OrderStatusEnum.FINISHED.getCode().equals(o.getOrderStatus()))
                    .map(Order::getId)
                    .toList();
            Set<Long> reviewedOrderIds = finishedOrderIds.isEmpty()
                    ? Set.of()
                    : new HashSet<>(productReviewMapper.findReviewedOrderIds(finishedOrderIds));
            Map<String, String> courierMap = logisticsBuilder.courierNameMap();
            list = orders.stream()
                    .map(o -> toVO(o, itemsByOrder.getOrDefault(o.getId(), List.of()),
                            reviewedOrderIds.contains(o.getId()), logisticsBuilder.build(o, courierMap)))
                    .toList();
        }
        return PageVO.<OrderVO>builder()
                .list(list)
                .total(total)
                .page(currentPage)
                .pageSize(size)
                .build();
    }

    @Override
    public OrderCountsVO orderCounts() {
        return orderMapper.countsByUser(currentUserId());
    }

    @Override
    @Transactional
    public OrderVO payOrder(Long id, String payType) {
        Order order = getOwnedOrder(id);
        // 支付成功直接进入「待发货」：原先的「待确认收款(2)」是真实支付场景下的商家人工对账环节，
        // 本项目支付是 mock 的，点一下即成功，不存在到账延迟，没必要再让商家逐单点一次确认
        checkTransition(order, OrderStatusEnum.PENDING_SHIP);
        LocalDateTime now = LocalDateTime.now();
        orderMapper.updateStatus(id, OrderStatusEnum.PENDING_SHIP.getCode(), payType, null, null, now);
        // 支付成功即生成收款单（同一事务）：一笔订单一张，财务后续按单审核
        financeReceiptService.createForPaidOrder(order, payType, now);
        // 券核销：LOCKED -> USED
        couponMapper.markUsedByOrder(id, LocalDateTime.now());
        // 支付成功后推「新订单提醒」给后台管理端（推送失败不影响主流程）
        pushNewOrderNotice(order);
        return getOrder(id);
    }

    // 组装并推送新订单提醒消息：type + orderId + content，序列化成 JSON 发给在线后台连接
    private void pushNewOrderNotice(Order order) {
        Map<String, Object> msg = Map.of(
                "type", 1,
                "orderId", order.getOrderNo(),
                "content", "您有新订单，请及时处理"
        );
        try {
            String json = objectMapper.writeValueAsString(msg);
            webSocketSessionRegistry.broadcast(json);
        } catch (Exception e) {
            log.warn("推送新订单提醒失败: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public OrderVO shipOrder(Long id, String courier, String trackingNumber) {
        Order order = getOwnedOrder(id);
        checkTransition(order, OrderStatusEnum.SHIPPED);
        orderMapper.updateStatus(id, OrderStatusEnum.SHIPPED.getCode(), null, courier, trackingNumber, LocalDateTime.now());
        return getOrder(id);
    }

    @Override
    @Transactional
    public OrderVO finishOrder(Long id) {
        Order order = getOwnedOrder(id);
        checkTransition(order, OrderStatusEnum.FINISHED);
        orderMapper.updateStatus(id, OrderStatusEnum.FINISHED.getCode(), null, null, null, LocalDateTime.now());
        return getOrder(id);
    }

    @Override
    @Transactional
    public OrderVO cancelOrder(Long id) {
        Order order = getOwnedOrder(id);
        checkTransition(order, OrderStatusEnum.CANCEL);
        restoreStock(order.getId());
        // 已支付未发货（status 3）取消 → 自动生成退款售后单（整单退）
        if (OrderStatusEnum.PENDING_SHIP.getCode().equals(order.getOrderStatus())) {
            autoRefundOnCancel(order);
        }
        orderMapper.updateStatus(id, OrderStatusEnum.CANCEL.getCode(), null, null, null, LocalDateTime.now());
        // 取消订单退回券：未支付解锁；已支付取消伴随整单退款，已用券在有效期内返还
        couponMapper.releaseLockedByOrder(id);
        couponMapper.releaseUsedByOrder(id, LocalDateTime.now());
        return getOrder(id);
    }

    @Override
    @Transactional
    public void deleteOrder(Long id) {
        // 软删：只校验存在 + 归属（不校验状态，任何状态的订单都能删）；is_deleted=1 后列表不再返回
        Order order = getOwnedOrder(id);
        orderMapper.delete(id, LocalDateTime.now());
    }

    @Override
    public LogisticsVO getLogistics(Long id) {
        return buildLogistics(getOwnedOrder(id));
    }

    /** 超时关闭待付款订单：执行周期由后台动态任务配置控制，默认每分钟一次 */
    @Transactional
    @MonitoredTask(name = "closeExpiredOrders", description = "超时关闭待付款订单并恢复库存",
            cron = "每 60 秒")
    public void sweepExpiredPendingPayOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(PENDING_PAY_TIMEOUT_SECONDS);
        List<Order> expired = orderMapper.findExpiredPendingPay(OrderStatusEnum.PENDING_PAY.getCode(), threshold);
        for (Order order : expired) {
            // 二次确认仍是待付款，避免与支付并发时误取消已付款订单
            Order latest = orderMapper.findById(order.getId());
            if (latest == null || !latest.getOrderStatus().equals(OrderStatusEnum.PENDING_PAY.getCode())) {
                continue;
            }
            restoreStock(order.getId());
            // 只改状态为取消，不软删除：待付款订单是有效订单记录，超时关闭只是状态流转（pending_pay → cancel），
            // 保留下来用户才能在订单列表看到「这笔订单超时取消了」
            orderMapper.updateStatus(order.getId(), OrderStatusEnum.CANCEL.getCode(), null, null, null, LocalDateTime.now());
            // 超时关闭：退回锁定中的券
            couponMapper.releaseLockedByOrder(order.getId());
        }
        if (!expired.isEmpty()) {
            log.info("超时关闭待付款订单 {} 条", expired.size());
        }
    }

    /**
     * 未支付提醒：每分钟扫一次，订单失效前 N 分钟推送一次性订阅消息。
     * <p>
     * 订单 30 分钟失效，默认提前 10 分钟（即下单后第 20 分钟）推送，
     * 保证用户收到时还有时间付款，且 time8 填入的是未来时间。
     * 只处理：待付款 + 用户已授权 + 从未推送过的订单；推送成功或永久失败后标记 sent=1，避免重复推送。
     */
    @MonitoredTask(name = "unpaidNotify", description = "待付款订单订阅消息提醒",
            cron = "每 60 秒")
    public void sweepUnpaidNotify() {
        // 提前分钟数限制在 1～29，防止配置错误把窗口算反
        int beforeMinutes = Math.max(1, Math.min(29, subscribeProperties.getNotifyBeforeMinutes()));
        // created_at <= now - (30 - before) 分钟时进入推送窗口
        LocalDateTime createdBefore = LocalDateTime.now()
                .minusSeconds(PENDING_PAY_TIMEOUT_SECONDS - beforeMinutes * 60L);

        List<Order> candidates = orderMapper.findPendingUnpaidToNotify(
                OrderStatusEnum.PENDING_PAY.getCode(), createdBefore);
        for (Order order : candidates) {
            try {
                // 二次确认仍是待付款，避免扫描后刚好支付/取消
                Order latest = orderMapper.findById(order.getId());
                if (latest == null
                        || !OrderStatusEnum.PENDING_PAY.getCode().equals(latest.getOrderStatus())) {
                    continue;
                }
                LocalDateTime expireAt = latest.getCreatedAt().plusSeconds(PENDING_PAY_TIMEOUT_SECONDS);
                // 已经过期就交给超时关闭任务，避免 time8 填过去时间
                if (!expireAt.isAfter(LocalDateTime.now())) {
                    continue;
                }
                // 一次性订阅消息需要 openid
                String openid = userMapper.findOpenidById(latest.getUserId());
                if (openid == null || openid.isBlank()) {
                    log.warn("未支付提醒跳过：用户无 openid, orderNo={}", latest.getOrderNo());
                    continue;
                }

                List<OrderItem> items = orderItemMapper.listByOrderId(latest.getId());
                String productName = buildNotifyProductName(items);

                SendResult result = weChatSubscribeMessageClient.sendUnpaidReminder(
                        openid, productName, latest.getOrderNo(), latest.getPayAmount(), expireAt);

                // 成功或永久失败（用户拒收/无配额等）都标记，避免每分钟重试
                if (result == SendResult.SUCCESS || result == SendResult.PERMANENT_FAIL) {
                    orderMapper.markUnpaidNotifySent(latest.getId(), LocalDateTime.now());
                }
            } catch (Exception e) {
                // 单笔失败不影响其他订单
                log.warn("未支付提醒处理异常, orderId={}", order.getId(), e);
            }
        }
    }

    /** 订阅消息 thing1：取第一件商品名，多件追加「等N件」，最终长度由客户端截断到 20 字 */
    private String buildNotifyProductName(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return "商品";
        }
        String name = items.get(0).getProductName();
        return items.size() > 1 ? name + "等" + items.size() + "件" : name;
    }

    /** 自动确认收货：执行周期由后台动态任务配置控制，默认每分钟一次；
     *  ship_at 超过 7 天的已发货订单自动变已完成，无论用户是否主动签收 */
    @Transactional
    @MonitoredTask(name = "autoConfirmOrders", description = "发货 7 天后自动确认收货",
            cron = "每 60 秒")
    public void sweepAutoConfirmOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(AUTO_CONFIRM_DAYS);
        List<Order> shipped = orderMapper.findExpiredShipped(OrderStatusEnum.SHIPPED.getCode(), threshold);
        for (Order order : shipped) {
            // 二次确认仍是已发货，避免与手动确认收货/其它流转并发时误改
            Order latest = orderMapper.findById(order.getId());
            if (latest == null || !latest.getOrderStatus().equals(OrderStatusEnum.SHIPPED.getCode())) {
                continue;
            }
            // 自动完成：已发货 → 已完成，sign_status=auto 与用户主动签收(signed)区分
            orderMapper.autoConfirm(order.getId(), LocalDateTime.now());
        }
        if (!shipped.isEmpty()) {
            log.info("自动确认收货订单 {} 条", shipped.size());
        }
    }

    // ==================== 内部工具 ====================

    private Order getOwnedOrder(Long id) {
        Order order = orderMapper.findById(id);
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

    private void restoreStock(Long orderId) {
        List<OrderItem> items = orderItemMapper.listByOrderId(orderId);
        for (OrderItem item : items) {
            if (item.getSkuId() != null) {
                // SKU 级回补 → 委托 product-service /internal/stock-write(IN)
                stockClient.write(item.getSkuId(), item.getSkuCode(), "IN", "订单取消回补", item.getQuantity(), item.getId());
            } else {
                // 无 SKU 的旧数据兜底：product 级回补（SKU 改造后这条不应该走到）
                productMapper.restoreStock(item.getProductId(), item.getQuantity());
            }
        }
    }

    /**
     * 解析下单 SKU：指定 skuId 时必须属于该商品且已启用；
     * 未指定时回落到商品默认 SKU（单规格/旧客户端），查不到返回 null 走商品级兜底。
     */
    private ProductSku resolveSku(Long productId, Long skuId) {
        ProductSku sku = skuId != null
                ? skuMapper.findById(skuId)
                : skuMapper.findDefaultByProductId(productId);
        if (sku == null) {
            if (skuId != null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "所选规格不存在或已下架");
            }
            return null;
        }
        if (!productId.equals(sku.getProductId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "所选规格与商品不匹配");
        }
        if (sku.getStatus() == null || sku.getStatus() != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "所选规格已下架");
        }
        return sku;
    }

    /** 取消订单自动退款：生成 refunded 售后单（整单退，退款跟着原订单 pay_at 归属营业额） */
    private void autoRefundOnCancel(Order order) {
        // 已有售后单则跳过（退款已走售后流程，不重复退）
        if (afterSaleRefundMapper.countAfterSale(order.getId()) > 0) {
            return;
        }
        List<OrderItem> items = orderItemMapper.listByOrderId(order.getId());
        if (items.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> params = new HashMap<>();
        params.put("orderId", order.getId());
        params.put("orderNo", order.getOrderNo());
        params.put("afterSaleNo", "AS" + now.format(AFTER_SALE_NO_FORMATTER));
        params.put("now", now);
        afterSaleRefundMapper.insertAfterSale(params);
        Long afterSaleId = ((Number) params.get("id")).longValue();
        afterSaleRefundMapper.insertItems(afterSaleId, items, now);
        afterSaleRefundMapper.insertOnlyRefund(afterSaleId, now);
    }

    private String generateOrderNo() {
        // order_no 有唯一索引，秒级精度同秒会撞；毫秒 + 4 位随机数保证并发下不重复
        return "ORD" + LocalDateTime.now().format(ORDER_NO_FORMATTER)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    /**
     * 生成假物流轨迹：轨迹逻辑见 {@link LogisticsBuilder}（C 端与后台共用一份，避免两边跑偏）。
     * 用订单 id 做种子让每单的城市/路线/快递员不同；courier 存英文 key，这里转中文。
     */
    private LogisticsVO buildLogistics(Order order) {
        return logisticsBuilder.build(order);
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

    /** 单条路径（详情/下单/支付等）：逐单查明细与评价，物流按订单单独构建 */
    private OrderVO toVO(Order order) {
        List<OrderItem> items = orderItemMapper.listByOrderId(order.getId());
        // 是否已评价：仅已完成订单可能有评价记录，其余状态直接 false（省去无谓查询）
        boolean reviewed = OrderStatusEnum.FINISHED.getCode().equals(order.getOrderStatus())
                && productReviewMapper.findByOrderId(order.getId()) != null;
        return toVO(order, items, reviewed, buildLogistics(order));
    }

    /** 批量装配路径（订单列表分页）：items/reviewed/logistics 均由调用方批量预取，内部不再查库 */
    private OrderVO toVO(Order order, List<OrderItem> items, boolean reviewed, LogisticsVO logistics) {
        OrderStatusEnum status = OrderStatusEnum.fromCode(order.getOrderStatus());
        PayTypeEnum payType = PayTypeEnum.fromCode(order.getPayType());
        // courier 存英文 key，转中文返回；字典表优先，与物流轨迹里的名称保持一致。
        // 订单未填快递公司时保持原值（不采用轨迹里的兜底随机名）
        String courierDesc = order.getCourier() == null || order.getCourier().isBlank()
                ? order.getCourier()
                : logistics.getCourier();
        // 物流摘要：已发货/已完成订单取最新一条轨迹，列表卡片直接展示，避免每卡一次请求
        String logisticsStatus = null;
        String latestTrace = null;
        if (OrderStatusEnum.SHIPPED.getCode().equals(order.getOrderStatus())
                || OrderStatusEnum.FINISHED.getCode().equals(order.getOrderStatus())) {
            List<LogisticsTraceVO> traces = logistics.getTraces();
            if (!traces.isEmpty()) {
                LogisticsTraceVO latest = traces.get(traces.size() - 1);
                logisticsStatus = latest.getStatus();
                latestTrace = latest.getDesc();
            }
        }
        return OrderVO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .payAmount(order.getPayAmount())
                .originalAmount(order.getOriginalAmount())
                .discountAmount(order.getDiscountAmount())
                .couponId(order.getCouponId())
                .couponName(order.getCouponName())
                .couponAmount(order.getCouponAmount())
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
                .courier(courierDesc)
                .trackingNumber(order.getTrackingNumber())
                .signStatus(order.getSignStatus())
                .logisticsStatus(logisticsStatus)
                .latestTrace(latestTrace)
                .createdAt(order.getCreatedAt())
                .payAt(order.getPayAt())
                .shipAt(order.getShipAt())
                .finishAt(order.getFinishAt())
                .reviewed(reviewed)
                .afterSale(order.getAfterSale())
                .items(items.stream().map(this::toItemVO).toList())
                .build();
    }

    private OrderItemVO toItemVO(OrderItem item) {
        return OrderItemVO.builder()
                .orderItemId(item.getId())
                .productId(item.getProductId())
                .skuId(item.getSkuId())
                .skuCode(item.getSkuCode())
                .specJson(item.getSpecJson())
                .productName(item.getProductName())
                .image(item.getImage())
                .salesPrice(item.getSalesPrice())
                .originalPrice(item.getOriginalPrice())
                .discountAmount(item.getDiscountAmount())
                .quantity(item.getQuantity())
                .subTotal(item.getSubTotal())
                .afterSaleStatus(item.getAfterSaleStatus())
                .afterSaleId(item.getAfterSaleId())
                .build();
    }
}