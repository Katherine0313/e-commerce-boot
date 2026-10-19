package ecommerce.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 订单项 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemVO {
    @Schema(description = "商品 ID")
    private Long productId;
    @Schema(description = "商品名快照")
    private String productName;
    @Schema(description = "商品图快照")
    private String image;
    @Schema(description = "成交单价")
    private BigDecimal salesPrice;
    @Schema(description = "数量")
    private Integer quantity;
    @Schema(description = "当前库存（后台详情展示，发货弹窗判断发货状态用）")
    private Integer stock;
    @Schema(description = "小计")
    private BigDecimal subTotal;
}
