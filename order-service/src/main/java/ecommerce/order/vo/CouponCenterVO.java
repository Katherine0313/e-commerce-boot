package ecommerce.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 领券中心券卡片。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponCenterVO {
    private Long templateId;
    private String name;
    private String type;
    private String typeDesc;
    private BigDecimal faceValue;
    private BigDecimal discountRate;
    private BigDecimal maxDiscount;
    private BigDecimal thresholdAmount;
    private String scopeTypeDesc;
    private String targetGroupDesc;
    @Schema(description = "最低会员等级 ID，0 = 不限")
    private Long minMemberLevel;
    @Schema(description = "有效期说明，如「固定 2026-11-01 ~ 2026-11-30」")
    private String validityDesc;
    private Integer remainQuantity;
    @Schema(description = "当前用户是否已领取")
    private Boolean claimed;
    private String description;
}
