package ecommerce.aftersale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 申请售后请求（整单售后）。
 */
@Data
public class CreateAfterSaleDTO {
    @Schema(description = "关联订单 ID")
    private Long orderId;
    @Schema(description = "售后类型：only_refund 仅退款 / return_refund 退货退款 / exchange 换货")
    private String afterSaleType;
    @Schema(description = "售后原因")
    private String reason;
    @Schema(description = "买家备注")
    private String remark;
    @Schema(description = "图片凭证 URL 数组")
    private List<String> images;
}
