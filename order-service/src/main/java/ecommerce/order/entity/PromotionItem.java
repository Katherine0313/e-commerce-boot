package ecommerce.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 活动商品（order-service 只读 promotion_item 表）。
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
