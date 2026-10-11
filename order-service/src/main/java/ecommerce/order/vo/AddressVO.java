package ecommerce.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 收货地址 VO（返回前端）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressVO {
    @Schema(description = "地址 ID")
    private Long id;
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
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
