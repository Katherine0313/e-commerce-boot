package ecommerce.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商品规格参数项（非 SKU）：按数组顺序展示。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpecItemVO {
    @Schema(description = "参数名")
    private String name;
    @Schema(description = "参数值")
    private String value;
}
