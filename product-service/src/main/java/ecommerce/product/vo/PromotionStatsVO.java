package ecommerce.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 活动效果统计。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionStatsVO {
    @Schema(description = "使用该活动的订单数（含未支付）")
    private Long orderCount;
    @Schema(description = "已支付订单数")
    private Long paidOrderCount;
    @Schema(description = "累计优惠金额")
    private BigDecimal discountAmount;
}
