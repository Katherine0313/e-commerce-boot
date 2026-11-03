package ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 发货 / 补发请求体。
 * <p>
 * 快递公司与快递单号必填（前端 rules + 后端校验都拦一道）。
 */
@Data
public class DeliveryCreateDTO {
    @Schema(description = "快递公司编码（delivery_express.code）")
    private String expressCode;
    @Schema(description = "快递单号")
    private String trackingNumber;
    @Schema(description = "发货备注")
    private String remark;
}
