package ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 后台包裹列表查询条件。
 */
@Data
public class DeliveryQueryDTO {
    @Schema(description = "订单号（模糊）")
    private String orderNo;
    @Schema(description = "快递单号（模糊）")
    private String trackingNumber;
    @Schema(description = "快递公司编码")
    private String expressCode;
    @Schema(description = "发货类型：1 正常发货 2 补发")
    private Integer deliveryType;
}
