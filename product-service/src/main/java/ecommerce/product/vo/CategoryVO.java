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
    @Schema(description = "分类 ID")
    private Long id;
    @Schema(description = "分类名称")
    private String name;
    @Schema(description = "父分类 ID，顶级分类为 null")
    private Long parentId;
    @Schema(description = "排序值，越小越靠前")
    private Integer sort;
    @Schema(description = "状态：1 显示 0 隐藏")
    private Integer status;
    @Schema(description = "是否推荐：1 是 0 否")
    private Integer recommend;
    @Schema(description = "该分类下直接挂靠的商品数")
    private Integer productCount;
}
