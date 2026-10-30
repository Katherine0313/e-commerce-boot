package ecommerce.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 活动商品（promotion_item 表）。
 * <p>
 * 直减类必填活动价；满减类只用来圈定商品范围，活动价为空。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionItem {
    private Long id;
    private Long promotionId;
    private Long productId;
    private BigDecimal promotionPrice;
}
