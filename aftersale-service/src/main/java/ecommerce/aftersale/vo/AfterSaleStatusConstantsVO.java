package ecommerce.aftersale.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 售后状态常量 VO（前端取中文映射用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSaleStatusConstantsVO {
    @Schema(description = "状态 code → 中文")
    private Map<String, String> map;
    @Schema(description = "状态 code → 颜色")
    private Map<String, String> color;
    @Schema(description = "原因 code → 中文（全量）")
    private Map<String, String> reasonMap;
    @Schema(description = "未发货原因 code → 中文")
    private Map<String, String> unshippedReasonMap;
    @Schema(description = "已发货原因 code → 中文")
    private Map<String, String> shippedReasonMap;
    @Schema(description = "类型 code → 中文")
    private Map<String, String> typeMap;
    @Schema(description = "货物状态 code → 中文")
    private Map<String, String> goodsStatusMap;
}
