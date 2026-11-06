package ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 销售规格维度（products.sku_specs 数组元素）。
 */
@Data
public class SkuSpecDTO {
    @Schema(description = "规格字典 ID，自定义规格可为空")
    private Long groupId;
    @Schema(description = "规格名，如 颜色 / 内存")
    private String name;
    @Schema(description = "可选值，顺序决定 SKU 生成顺序")
    private List<String> values = new ArrayList<>();
}
