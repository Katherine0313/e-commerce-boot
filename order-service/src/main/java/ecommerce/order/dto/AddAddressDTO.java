package ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 新增收货地址请求。
 */
@Data
public class AddAddressDTO {
    @Schema(description = "收货人姓名")
    private String consignee;
    @Schema(description = "手机号")
    private String phone;
    @Schema(description = "省市区")
    private String region;
    @Schema(description = "详细地址")
    private String detailAddress;
    @Schema(description = "是否默认地址：1 是 0 否")
    private Integer isDefault;
}
