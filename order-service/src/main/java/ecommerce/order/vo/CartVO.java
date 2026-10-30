package ecommerce.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartVO {
    @Schema(description = "商品 ID")
    private Long productId;
    @Schema(description = "商品名快照")
    private String name;
    @Schema(description = "商品图快照")
    private String image;
    @Schema(description = "单价（有活动时为活动价）")
    private BigDecimal price;
    @Schema(description = "活动前单价（无活动为 null）")
    private BigDecimal originalPrice;
    @Schema(description = "促销标签（无活动为 null）")
    private String promotionTag;
    @Schema(description = "商品描述快照")
    private String description;
    @Schema(description = "数量")
    private Integer quantity;
    @Schema(description = "增值保障明细（数组，暂未启用为空）")
    private List<Map<String, Object>> insuranceDetail;
    @Schema(description = "增值保障费")
    private BigDecimal insuranceFee;
    @Schema(description = "库存数量（前端校验数量上限用）")
    private Integer stock;
    @Schema(description = "单品小计 = (单价 + 增值保障费) × 数量")
    private BigDecimal subTotal;
}

