package ecommerce.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 工作台商品统计 VO（/api/admin/products/dashboard）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardVO {
    @Schema(description = "商品总数")
    private long totalProducts;
    @Schema(description = "总库存")
    private long totalStock;
    @Schema(description = "库存总价值（售价 × 库存）")
    private BigDecimal totalValue;
}
