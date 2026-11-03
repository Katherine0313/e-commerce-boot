package ecommerce.aftersale.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 售后单当前可执行的动作（后台审核按钮）。
 * <p>
 * 由后端按状态机下发，前端纯渲染，不自己维护状态表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSaleActionVO {

    @Schema(description = "动作执行后的目标状态 code")
    private String status;

    @Schema(description = "按钮文案")
    private String label;

    @Schema(description = "按钮类型：PRIMARY 正向 / DANGER 危险")
    private String kind;
}
