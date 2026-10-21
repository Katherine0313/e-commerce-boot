package ecommerce.aftersale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 提交退货方式请求。
 */
@Data
public class SubmitReturnDTO {
    @Schema(description = "退货方式：pickup 上门取件 / self 自行寄回")
    private String returnWay;
    @Schema(description = "取件时段：morning 上午 / afternoon 下午（上门取件必填）")
    private String returnTimeSlot;
    @Schema(description = "退货快递公司（退货退款自行寄回必填）")
    private String courier;
    @Schema(description = "退货快递单号（退货退款自行寄回必填）")
    private String trackingNumber;
}
