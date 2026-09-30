package ecommerce.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标签 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagVO {
    /** 标签 ID */
    @Schema(description = "标签 ID")
    private Long id;
    /** 标签名称 */
    @Schema(description = "标签名称")
    private String name;
}
