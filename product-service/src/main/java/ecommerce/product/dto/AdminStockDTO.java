package ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 后台库存预警新增/编辑请求体（/api/admin/stock）。
 * <p>
 * product_id 重复时后端自动转更新（upsert），字段统一驼峰。
 */
@Data
public class AdminStockDTO {
    @Schema(description = "商品 ID")
    private Long productId;
    @Schema(description = "预警是否启用：1 启用 0 禁用")
    private Integer alertEnabled;
    @Schema(description = "库存下限（启用预警时必填）")
    private Integer minStock;
    @Schema(description = "库存上限（启用预警时必填）")
    private Integer maxStock;
}
