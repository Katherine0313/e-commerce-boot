package ecommerce.common.promotion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 满减阶梯配置：满 thresholdAmount 减 discountAmount，或按 discountRate 打折。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionRuleConfig {
    /** 满额门槛 */
    private BigDecimal thresholdAmount;
    /** 满减金额（满额减用） */
    private BigDecimal discountAmount;
    /** 折扣率（满额折用，0.85 = 85 折） */
    private BigDecimal discountRate;
}
