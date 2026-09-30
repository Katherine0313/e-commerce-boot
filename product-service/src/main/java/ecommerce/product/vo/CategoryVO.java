package ecommerce.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分类 VO。parentId 构成分类树，顶级分类 parentId 为 null。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryVO {
    /** 分类 ID */
    @Schema(description = "分类 ID")
    private Long id;
    /** 分类名称 */
    @Schema(description = "分类名称")
    private String name;
    /** 父分类 ID */
    @Schema(description = "父分类 ID，顶级分类为 null")
    private Long parentId;
    /** 排序值 */
    @Schema(description = "排序值，越小越靠前")
    private Integer sort;
}
