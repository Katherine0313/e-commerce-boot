package ecommerce.common.promotion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 算价结果：一行商品的活动后价格与优惠。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteItemResult {
    private Long productId;
    private Integer quantity;
    /** 活动前单价 */
    private BigDecimal originalPrice;
    /** 活动后单价（无活动时等于原价） */
    private BigDecimal finalPrice;
    /** 活动前小计 = 原价 × 数量 */
    private BigDecimal originalSubTotal;
    /** 优惠后小计（不含保险） */
    private BigDecimal subTotal;
    /** 该行总优惠（直减 + 满减分摊 + 券分摊） */
    private BigDecimal discountAmount;
    /** 该行分摊到的优惠券优惠 */
    private BigDecimal couponDiscountAmount;
    /** 命中的直减活动 id（无则为 null） */
    private Long promotionId;
    /** 命中的直减活动名称 */
    private String promotionName;
    /** C 端促销标签 */
    private String promotionTag;
}
