package ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 下单明细项。
 */
@Data
public class OrderItemDTO {
    @Schema(description = "商品 ID")
    private Long productId;
    @Schema(description = "SKU ID；为空时后端回落到商品默认 SKU（兼容旧客户端）")
    private Long skuId;
    @Schema(description = "数量")
    private Integer quantity;
    @Schema(description = "增值保障明细（前端勾选的保障项，后端按规则复核后重算）")
    private String insuranceDetail;
    @Schema(description = "增值保障费（后端按规则重算，不采信前端值）")
    private BigDecimal insuranceFee;
}
