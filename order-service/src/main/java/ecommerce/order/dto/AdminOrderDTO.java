package ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 后台订单更新请求体（/api/admin/orders/{id}）。
 * <p>
 * 字段可空，支持局部更新（收货人/电话/地址/备注）。
 */
@Data
public class AdminOrderDTO {
    @Schema(description = "收货人姓名")
    private String consignee;
    @Schema(description = "收货人手机号")
    private String phone;
    @Schema(description = "省市区")
    private String region;
    @Schema(description = "详细地址")
    private String detailAddress;
    @Schema(description = "商家备注")
    private String remark;
}
