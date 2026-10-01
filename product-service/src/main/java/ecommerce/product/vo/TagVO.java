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
    @Schema(description = "标签 ID")
    private Long id;
    @Schema(description = "标签名称")
    private String name;
    @Schema(description = "标签编码")
    private String code;
    @Schema(description = "标签类型")
    private String type;
    @Schema(description = "标签颜色")
    private String color;
    @Schema(description = "排序值")
    private Integer sort;
    @Schema(description = "状态：1 显示 0 隐藏")
    private Integer status;
    @Schema(description = "关联商品数")
    private Integer productCount;
}
