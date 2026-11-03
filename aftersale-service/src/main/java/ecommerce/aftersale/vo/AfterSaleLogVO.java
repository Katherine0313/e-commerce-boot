package ecommerce.aftersale.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 售后变更流水 VO。
 * <p>
 * 买家接口不返回 merchantRemark（商家内部备注），后台接口返回。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSaleLogVO {

    @Schema(description = "流水 ID")
    private Long id;

    @Schema(description = "售后单 ID")
    private Long afterSaleId;

    @Schema(description = "操作人类型：1买家 2后台管理员")
    private Integer userType;

    @Schema(description = "操作人类型中文")
    private String userTypeDesc;

    @Schema(description = "变更前状态 code（新建时为空）")
    private String beforeStatus;

    @Schema(description = "变更前状态中文")
    private String beforeStatusDesc;

    @Schema(description = "变更后状态 code")
    private String afterStatus;

    @Schema(description = "变更后状态中文")
    private String afterStatusDesc;

    @Schema(description = "操作类型 code")
    private Integer operateType;

    @Schema(description = "操作类型中文")
    private String operateTypeDesc;

    @Schema(description = "公开说明（买家可见）")
    private String content;

    @Schema(description = "商家内部备注（仅后台接口返回）")
    private String merchantRemark;

    @Schema(description = "发生时间")
    private LocalDateTime createdAt;
}
