package ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 规格库：规格名新增/编辑入参。
 */
@Data
public class SpecGroupDTO {
    @Schema(description = "规格名，如 颜色")
    private String name;
    @Schema(description = "所属分类，NULL 为全局")
    private Long categoryId;
    @Schema(description = "1 启用 / 0 停用")
    private Integer status;
}
