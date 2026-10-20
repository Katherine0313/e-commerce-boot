package ecommerce.aftersale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 后台售后审核请求体（/api/admin/after-sales/{id}）。
 */
@Data
public class AdminAfterSaleDTO {
    @Schema(description = "目标售后状态 code（refunding/returning/refund_confirming/refunded/rejected 等）")
    private String status;
    @Schema(description = "商家内部备注")
    private String merchantRemark;
}
