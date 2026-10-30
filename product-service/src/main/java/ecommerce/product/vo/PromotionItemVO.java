package ecommerce.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 后台活动商品 VO（编辑回填 / 详情展示用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionItemVO {
    @Schema(description = "商品 ID")
    private Long productId;
    @Schema(description = "商品名称")
    private String productName;
    @Schema(description = "商品当前售价")
    private BigDecimal salesPrice;
    @Schema(description = "活动价（满减类为空）")
    private BigDecimal promotionPrice;
}
