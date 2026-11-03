package ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 后台新建 / 编辑快递公司请求体。
 */
@Data
public class DeliveryExpressDTO {
    @Schema(description = "快递公司编码（英文 key，如 shunfeng；新建时必填且唯一）")
    private String code;
    @Schema(description = "快递公司名称")
    private String name;
    @Schema(description = "logo 相对路径")
    private String logo;
    @Schema(description = "排序（小的在前）")
    private Integer sortOrder;
    @Schema(description = "状态：1 启用 0 禁用")
    private Integer status;
    @Schema(description = "备注")
    private String remark;
}
