package ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 规格库：规格值新增入参。
 */
@Data
public class SpecValueDTO {
    @Schema(description = "所属规格名 ID")
    private Long groupId;
    @Schema(description = "规格值，如 曜石黑")
    private String value;
}
