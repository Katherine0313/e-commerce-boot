package ecommerce.common.promotion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 算价入参：一行商品（当前原价 + 数量）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteItemInput {
    private Long productId;
    private Integer quantity;
    /** 商品当前销售价（活动前） */
    private BigDecimal originalPrice;
}
