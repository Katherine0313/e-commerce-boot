package ecommerce.order.service.impl;

import ecommerce.common.PageVO;
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
import ecommerce.order.vo.ReviewDetailVO;
import ecommerce.order.vo.ReviewSummaryVO;
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

        Long userId = currentUserId();
        Order order = getOwnedOrder(orderId, userId);

        // 只有「已完成」订单可评价
        if (!OrderStatusEnum.FINISHED.getCode().equals(order.getOrderStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "订单完成后才能评价");
        }
        // 一个订单只评一次
        if (productReviewMapper.findByOrderId(orderId) != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该订单已评价");
        }

        // 从订单明细定位被评价商品：优先匹配前端传的 productId，否则取订单首个商品
        List<OrderItem> items = orderItemMapper.listByOrderId(orderId);
        if (items.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "订单明细为空，无法评价");
        }
        OrderItem item = items.stream()
                .filter(i -> i.getProductId() != null && i.getProductId().equals(dto.getProductId()))
                .findFirst()
                .orElse(items.get(0));

        ProductReview review = ProductReview.builder()
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

    @Override
    public PageVO<ReviewDetailVO> listByProduct(Long productId, int page, int pageSize) {
        if (productId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "商品 ID 不能为空");
        }
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 50);
        int offset = (safePage - 1) * safeSize;
        List<ReviewDetailVO> list = productReviewMapper.listByProduct(productId, offset, safeSize);
        list.forEach(this::maskUserInfo);
        long total = productReviewMapper.countByProduct(productId);
        return new PageVO<>(list, total, safePage, safeSize);
    }

    @Override
    public ReviewSummaryVO summary(Long productId) {
        if (productId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "商品 ID 不能为空");
        }
        ReviewSummaryVO summary = productReviewMapper.summaryByProduct(productId);
        return summary == null ? ReviewSummaryVO.builder().build() : summary;
    }

    /**
     * C 端昵称处理：
     * <ul>
     *   <li>虚拟评价（user_id=0）→ 昵称统一「系统」，前端据此打「系统评价」标签</li>
     *   <li>手机号格式的昵称打码（138****5678），避免把真实手机号直接暴露在商品页</li>
     * </ul>
     */
    private void maskUserInfo(ReviewDetailVO vo) {
        if (Integer.valueOf(1).equals(vo.getIsVirtual())) {
            vo.setUserName("系统");
            vo.setUserAvatar(null);
            return;
        }
        String name = vo.getUserName();
        if (name != null && name.matches("1\\d{10}")) {
            vo.setUserName(name.substring(0, 3) + "****" + name.substring(7));
        }
    }

    // 查订单并校验归属（与 OrderServiceImpl.getOwnedOrder 同款规则，评价模块独立实现）
    private Order getOwnedOrder(Long orderId, Long userId) {
        Order order = orderMapper.findById(orderId);
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
