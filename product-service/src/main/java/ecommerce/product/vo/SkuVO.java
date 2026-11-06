package ecommerce.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * SKU 详情。C 端用于规格选择，后台用于编辑回显。
 */
@Data
public class SkuVO {
    @Schema(description = "SKU ID")
    private Long id;
    @Schema(description = "规格组合，如 {\"颜色\":\"曜石黑\",\"内存\":\"256G\"}")
    private Map<String, String> specJson;
    @Schema(description = "规格规范化串")
    private String specKey;
    @Schema(description = "售价")
    private BigDecimal price;
    @Schema(description = "市场价/划线价")
    private BigDecimal marketPrice;
    @Schema(description = "成本价（仅后台下发）")
    private BigDecimal costPrice;
    @Schema(description = "库存")
    private Integer stock;
    @Schema(description = "SKU 图，为空用商品主图")
    private String image;
    @Schema(description = "商家编码")
    private String skuCode;
    @Schema(description = "1 启用 / 0 停用")
    private Integer status;
}
