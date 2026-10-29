package ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 后台提交的规格参数项（非 SKU）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpecItemDTO {
    @Schema(description = "参数名")
    private String name;
    @Schema(description = "参数值")
    private String value;
}
