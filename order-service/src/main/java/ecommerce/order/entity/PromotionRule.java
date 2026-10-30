package ecommerce.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 满减阶梯（order-service 只读 promotion_rule 表）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionRule {
    private Long id;
    private Long promotionId;
    private BigDecimal thresholdAmount;
    private BigDecimal discountAmount;
    private BigDecimal discountRate;
}
