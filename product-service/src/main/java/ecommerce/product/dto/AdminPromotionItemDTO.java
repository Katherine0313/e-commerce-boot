package ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 活动商品请求项。
 */
@Data
public class AdminPromotionItemDTO {
    @Schema(description = "商品 ID")
    private Long productId;
    @Schema(description = "活动价（直减类必填，必须低于商品当前售价）")
    private BigDecimal promotionPrice;
}
