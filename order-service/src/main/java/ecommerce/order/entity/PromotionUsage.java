package ecommerce.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 促销使用记录（下单时由 order-service 写入，供后台统计）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionUsage {
    private Long id;
    private Long promotionId;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private BigDecimal discountAmount;
    private LocalDateTime createTime;
}
