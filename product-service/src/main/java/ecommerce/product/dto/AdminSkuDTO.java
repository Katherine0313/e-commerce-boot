package ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 后台 SKU 明细入参。id 为空表示新增，有 id 表示更新；未提交的旧 SKU 会被置为停用。
 */
@Data
public class AdminSkuDTO {
    @Schema(description = "SKU ID；为空表示新增")
    private Long id;
    @Schema(description = "规格组合，如 {\"颜色\":\"曜石黑\",\"内存\":\"256G\"}；单规格为空对象")
    private Map<String, String> specJson;
    @Schema(description = "售价")
    private BigDecimal price;
    @Schema(description = "市场价/划线价")
    private BigDecimal marketPrice;
    @Schema(description = "成本价")
    private BigDecimal costPrice;
    @Schema(description = "库存")
    private Integer stock;
    @Schema(description = "商家编码")
    private String skuCode;
    @Schema(description = "1 启用 / 0 停用")
    private Integer status;
}
