package ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 后台文章分类表单入参（新增 / 编辑共用）。
 */
@Data
public class AdminArticleCategoryDTO {

    @Schema(description = "分类名称")
    private String name;

    @Schema(description = "分类图标（可选）")
    private String picUrl;

    @Schema(description = "排序，越小越靠前")
    private Integer sortOrder;

    @Schema(description = "状态：1 启用 0 禁用")
    private Integer status;
}
