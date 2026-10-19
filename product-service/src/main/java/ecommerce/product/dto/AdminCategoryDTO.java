package ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 后台分类新增/编辑请求体（/api/admin/categories）。
 * <p>
 * 字段可空，支持局部更新；sort_order 不接收前端传值，排序只由 move 接口调整。
 */
@Data
public class AdminCategoryDTO {
    @Schema(description = "分类名称")
    private String name;
    @Schema(description = "父分类 ID，一级分类为 null")
    private Long parentId;
    @Schema(description = "状态：1 显示 0 隐藏")
    private Integer status;
    @Schema(description = "是否推荐：1 是 0 否")
    private Integer recommend;
    @Schema(description = "分类图片路径")
    private String image;
}
