package ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 后台标签新增/编辑请求体（/api/admin/tags）。
 * <p>
 * 字段可空，支持局部更新。
 */
@Data
public class AdminTagDTO {
    @Schema(description = "标签名称")
    private String name;
    @Schema(description = "标签编码（唯一）")
    private String code;
    @Schema(description = "标签类型，默认 custom")
    private String type;
    @Schema(description = "标签颜色，默认 #1890ff")
    private String color;
    @Schema(description = "排序值")
    private Integer sortOrder;
    @Schema(description = "状态：1 显示 0 隐藏")
    private Integer status;
}
