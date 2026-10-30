package ecommerce.common.promotion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 本次报价实际生效的活动汇总（下单时写 promotion_usage）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppliedPromotion {
    private Long promotionId;
    private String promotionName;
    /** DIRECT / FULL_REDUCE */
    private String type;
    /** 该活动带来的总优惠 */
    private BigDecimal discountAmount;
}
