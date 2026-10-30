package ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 满减阶梯请求项。
 */
@Data
public class AdminPromotionRuleDTO {
    @Schema(description = "满额门槛")
    private BigDecimal thresholdAmount;
    @Schema(description = "满减金额（满额减用）")
    private BigDecimal discountAmount;
    @Schema(description = "折扣率（满额折用，0.85 = 85 折）")
    private BigDecimal discountRate;
}
