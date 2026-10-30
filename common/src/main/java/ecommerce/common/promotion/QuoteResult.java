package ecommerce.common.promotion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 一次促销报价的完整结果。
 * <p>
 * 金额口径：originalAmount = Σ 原价 × 数量；payAmount = originalAmount - discountAmount（不含保险）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteResult {
    private List<QuoteItemResult> items;
    /** 活动前金额 */
    private BigDecimal originalAmount;
    /** 促销优惠（直减 + 满减） */
    private BigDecimal promotionDiscount;
    /** 优惠券优惠 */
    private BigDecimal couponDiscount;
    /** 总优惠 = 促销优惠 + 券优惠 */
    private BigDecimal discountAmount;
    /** 最终应付（不含保险） */
    private BigDecimal payAmount;
    /** 使用的优惠券 id / 名称（未用券为 null） */
    private Long couponId;
    private String couponName;
    /** 实际生效的活动（下单写使用记录用） */
    private List<AppliedPromotion> appliedPromotions;
}
