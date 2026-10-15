package ecommerce.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressVO {
    @Schema(description = "地址表的id")
    private Long id;
    @Schema(description = "收货人姓名")
    private String consignee;
    @Schema(description = "收货人手机号")
    private String phone;
    @Schema(description = "省，市，区")
    private String region;
    @Schema(description = "详细地址")
    private String detailAddress;
    @Schema(description = "是否为默认地址")
    private Integer isDefault;
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
