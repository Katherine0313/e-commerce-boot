package ecommerce.order.controller;

import ecommerce.common.Result;
import ecommerce.exception.BusinessException;
import ecommerce.order.entity.Order;
import ecommerce.order.entity.OrderItem;
import ecommerce.order.enums.OrderStatusEnum;
import ecommerce.order.mapper.OrderItemMapper;
import ecommerce.order.mapper.OrderMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 内部接口（/internal/ai/**）：Python AI 服务（FastAPI）内网调用，按用户查订单。
 * <p>
 * 鉴权靠 common 的 InternalApiInterceptor（X-Internal-Token 共享密钥），不加权限注解。
 * userId 由调用方显式传入（AI 客服只能查「当前对话用户」自己的订单，Python 侧保证来源）。
 */
@Tag(name = "AI 内部接口", description = "Python AI 服务内部调用（X-Internal-Token 鉴权）")
@RestController
@RequestMapping("/internal/ai")
public class InternalAiController {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    public InternalAiController(OrderMapper orderMapper, OrderItemMapper orderItemMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
    }

    @Operation(summary = "用户最近订单列表（keyword 匹配订单号/商品名）")
    @GetMapping("/orders")
    public Result<List<OrderBriefVO>> listOrders(@RequestParam(required = false) Long userId,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "5") Integer limit) {
        if (userId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "userId 不能为空");
        }
        int size = Math.max(1, Math.min(limit == null ? 5 : limit, 20));
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        // 复用 C 端分页查询（按创建时间倒序）：内部接口只取最近 N 条，即第一页
        List<Order> orders = orderMapper.listByUserPage(userId, kw, null, 0, size);
        if (orders.isEmpty()) {
            return Result.success(List.of());
        }
        // 明细按本批订单一次查回，避免逐单 N+1
        Map<Long, List<OrderItem>> itemsByOrder = orderItemMapper
                .listByOrderIds(orders.stream().map(Order::getId).toList()).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        List<OrderBriefVO> list = orders.stream()
                .map(o -> toBriefVO(o, itemsByOrder.getOrDefault(o.getId(), List.of())))
                .toList();
        return Result.success(list);
    }

    private OrderBriefVO toBriefVO(Order order, List<OrderItem> items) {
        OrderStatusEnum status = OrderStatusEnum.fromCode(order.getOrderStatus());
        return OrderBriefVO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .status(order.getOrderStatus())
                .statusDesc(status == null ? null : status.getDesc())
                .totalAmount(order.getPayAmount())
                .createdAt(order.getCreatedAt())
                .items(items.stream().map(this::toItemVO).toList())
                .build();
    }

    private OrderItemBriefVO toItemVO(OrderItem item) {
        return OrderItemBriefVO.builder()
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .price(item.getSalesPrice())
                .build();
    }

    /** AI 订单摘要（时间字段走全局 Jackson 格式 yyyy-MM-dd HH:mm:ss） */
    @Data
    @Builder
    public static class OrderBriefVO {
        private Long id;
        private String orderNo;
        /** 订单状态 code（见 OrderStatusEnum） */
        private Integer status;
        /** 订单状态中文描述 */
        private String statusDesc;
        /** 订单金额（orders.pay_amount） */
        private BigDecimal totalAmount;
        private LocalDateTime createdAt;
        private List<OrderItemBriefVO> items;
    }

    /** AI 订单明细项（商品名 + 数量 + 成交单价） */
    @Data
    @Builder
    public static class OrderItemBriefVO {
        private String productName;
        private Integer quantity;
        private BigDecimal price;
    }
}
