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
    @Schema(description = "数量")
    private Integer quantity;
    @Schema(description = "增值保障明细（暂未启用，忽略）")
    private String insuranceDetail;
    @Schema(description = "增值保障费（暂未启用，默认 0）")
    private BigDecimal insuranceFee;
}
