package ecommerce.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 满减阶梯（promotion_rule 表）：满 thresholdAmount 减 discountAmount，或按 discountRate 打折。
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
    private Integer sortOrder;
}
