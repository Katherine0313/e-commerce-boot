package ecommerce.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 满减阶梯 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionRuleVO {
    @Schema(description = "满额门槛")
    private BigDecimal thresholdAmount;
    @Schema(description = "满减金额")
    private BigDecimal discountAmount;
    @Schema(description = "折扣率")
    private BigDecimal discountRate;
}
