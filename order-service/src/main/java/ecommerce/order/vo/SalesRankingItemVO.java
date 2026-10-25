package ecommerce.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 销量排名项 VO（工作台 /api/admin/report/sales-ranking）。
 * <p>
 * 商品名/图片取订单项快照，不依赖商品当前信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "销量排名项")
public class SalesRankingItemVO {
    @Schema(description = "商品 ID")
    private Long productId;
    @Schema(description = "商品名快照")
    private String productName;
    @Schema(description = "商品图快照")
    private String image;
    @Schema(description = "销量（件）")
    private Long salesQuantity;
}
