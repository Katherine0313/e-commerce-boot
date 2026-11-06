package ecommerce.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 销售规格维度（下发给 C 端与后台）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkuSpecVO {
    @Schema(description = "规格字典 ID")
    private Long groupId;
    @Schema(description = "规格名")
    private String name;
    @Schema(description = "可选值")
    private List<String> values;
}
