package ecommerce.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类树 VO：一级父分类 + 其名下所有子分类（children）。
 * <p>
 * 用于 Redis 缓存 category:parent:{父id} 的 value，序列化为纯 JSON（不含 @class）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryTreeVO {
    @Schema(description = "分类 ID")
    private Long id;
    @Schema(description = "分类名称")
    private String name;
    @Schema(description = "父分类 ID，一级分类为 null")
    private Long parentId;
    @Schema(description = "排序值，越小越靠前")
    private Integer sort;
    @Schema(description = "状态：1 显示 0 隐藏")
    private Integer status;
    @Schema(description = "是否推荐：1 是 0 否")
    private Integer recommend;
    @Schema(description = "分类图片路径")
    private String image;
    @Schema(description = "该分类下直接挂靠的商品数")
    private Integer productCount;
    @Schema(description = "子分类列表")
    @Builder.Default
    private List<CategoryTreeVO> children = new ArrayList<>();
}
