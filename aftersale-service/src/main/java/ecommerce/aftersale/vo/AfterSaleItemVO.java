package ecommerce.aftersale.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 售后明细 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSaleItemVO {
    @Schema(description = "商品 ID")
    private Long productId;
    @Schema(description = "商品名快照")
    private String productName;
    @Schema(description = "商品图快照")
    private String image;
    @Schema(description = "成交单价")
    private BigDecimal salesPrice;
    @Schema(description = "售后数量")
    private Integer quantity;
    @Schema(description = "退款金额")
    private BigDecimal refundAmount;
    @Schema(description = "增值保障明细数组")
    private List<Map<String, Object>> insuranceDetail;
    @Schema(description = "增值保障费用")
    private BigDecimal insuranceFee;
}
