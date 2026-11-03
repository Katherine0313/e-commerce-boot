package ecommerce.aftersale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 后台售后审核请求体（/api/admin/after-sales/{id}）。
 */
@Data
public class AdminAfterSaleDTO {
    @Schema(description = "目标售后状态 code（refunding/returning/refunded/exchanging/exchanged/rejected，只能是状态机允许的跃迁）")
    private String status;
    @Schema(description = "商家内部备注（拒绝售后时必填）")
    private String merchantRemark;
}
