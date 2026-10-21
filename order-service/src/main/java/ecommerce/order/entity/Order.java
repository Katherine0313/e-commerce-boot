package ecommerce.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体（对应 orders 表）。
 * <p>
 * order_status 存数字（见 OrderStatusEnum），pay_at/confirm_at/ship_at/finish_at 由状态流转自动记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private Long id;
    private Long shopId;
    private Long userId;
    private String orderNo;
    private Integer isDeleted;
    private BigDecimal payAmount;
    private String payType;
    private Integer orderStatus;
    private String remark;
    private String consignee;
    private String phone;
    private String region;
    private String detailAddress;
    private String shippingType;
    private String courier;
    private String trackingNumber;
    private String signStatus;
    private LocalDateTime createdAt;
    private LocalDateTime payAt;
    private LocalDateTime shipAt;
    private LocalDateTime confirmAt;
    private LocalDateTime finishAt;
    private LocalDateTime updatedAt;
    // 是否已申请售后（查询时 EXISTS after_sales 带出，非本表列）
    private Boolean afterSale;
}
