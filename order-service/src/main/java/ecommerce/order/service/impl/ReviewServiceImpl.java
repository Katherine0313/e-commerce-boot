package ecommerce.order.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.order.dto.CreateReviewDTO;
import ecommerce.order.entity.Order;
import ecommerce.order.entity.OrderItem;
import ecommerce.order.entity.ProductReview;
import ecommerce.order.enums.OrderStatusEnum;
import ecommerce.order.mapper.OrderItemMapper;
import ecommerce.order.mapper.OrderMapper;
import ecommerce.order.mapper.ProductReviewMapper;
import ecommerce.order.service.ReviewService;
import ecommerce.order.vo.ReviewVO;
import ecommerce.utils.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品评价 Service 实现。
 * <p>
 * 约束照业务：只有「已完成」订单可评价、一个订单只评一次；商品名/图快照从 order_items 取，不信任前端。
 */
@Service
public class ReviewServiceImpl implements ReviewService {

    /** 模板店（杰米小店）：多租户沙箱接入前固定为 1 */
    private static final Long TEMPLATE_SHOP_ID = 1L;

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductReviewMapper productReviewMapper;

    public ReviewServiceImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                             ProductReviewMapper productReviewMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productReviewMapper = productReviewMapper;
    }

    @Override
    @Transactional
    public ReviewVO submit(Long orderId, CreateReviewDTO dto) {
        if (dto.getRating() == null || dto.getRating() < 1 || dto.getRating() > 5) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择 1~5 星评分");
        }
        if (dto.getContent() == null || dto.getContent().trim().length() < 5) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "评价内容至少 5 个字");
        }

        Long shopId = currentShopId();
        Long userId = currentUserId();
        Order order = getOwnedOrder(orderId, shopId, userId);

        // 只有「已完成」订单可评价
        if (!OrderStatusEnum.FINISHED.getCode().equals(order.getOrderStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "订单完成后才能评价");
        }
        // 一个订单只评一次
        if (productReviewMapper.findByOrderId(orderId, shopId) != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该订单已评价");
        }

        // 从订单明细定位被评价商品：优先匹配前端传的 productId，否则取订单首个商品
        List<OrderItem> items = orderItemMapper.listByOrderId(orderId, shopId);
        if (items.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "订单明细为空，无法评价");
        }
        OrderItem item = items.stream()
                .filter(i -> i.getProductId() != null && i.getProductId().equals(dto.getProductId()))
                .findFirst()
                .orElse(items.get(0));

        ProductReview review = ProductReview.builder()
                .shopId(shopId)
                .orderId(orderId)
                .userId(userId)
                .productId(item.getProductId())
                .productName(item.getProductName())
                .productImage(item.getImage())
                .rating(dto.getRating())
                .tag(dto.getTag())
                .content(dto.getContent().trim())
                .images(dto.getImages())
                // createdAt 用 JVM 的 LocalDateTime.now()，不用 MySQL NOW()（数据库时区 UTC 会差 8 小时）
                .createdAt(LocalDateTime.now())
                .build();
        productReviewMapper.insert(review);

        return toVO(review);
    }

    // 查订单并校验归属（与 OrderServiceImpl.getOwnedOrder 同款规则，评价模块独立实现）
    private Order getOwnedOrder(Long orderId, Long shopId, Long userId) {
        Order order = orderMapper.findById(orderId, shopId);
        if (order == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权操作该订单");
        }
        return order;
    }

    private Long currentUserId() {
        return UserContext.getUserId();
    }

    // 当前店铺：C 端 user 令牌带 shopId（多租户隔离键），无 shopId 时回退模板店
    private Long currentShopId() {
        Long shopId = UserContext.getShopId();
        return shopId == null ? TEMPLATE_SHOP_ID : shopId;
    }

    private ReviewVO toVO(ProductReview review) {
        return ReviewVO.builder()
                .id(review.getId())
                .orderId(review.getOrderId())
                .productId(review.getProductId())
                .productName(review.getProductName())
                .productImage(review.getProductImage())
                .rating(review.getRating())
                .tag(review.getTag())
                .content(review.getContent())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
