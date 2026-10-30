package ecommerce.product.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台新建 / 编辑促销活动请求。
 */
@Data
public class AdminPromotionDTO {
    @Schema(description = "活动名称")
    private String name;
    @Schema(description = "类型：DIRECT 直减 / FULL_REDUCE 满减")
    private String type;
    @Schema(description = "玩法：DISCOUNT / NEW_USER / FULL_AMOUNT_REDUCE / FULL_AMOUNT_DISCOUNT")
    private String ruleType;
    @Schema(description = "目标人群：ALL / NEW_USER")
    private String targetGroup;
    @Schema(description = "商品范围：ALL 全场 / PRODUCT 指定商品")
    private String scopeType;
    @Schema(description = "C 端促销标签，如「限时立减」")
    private String tagName;
    @Schema(description = "开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @Schema(description = "结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    @Schema(description = "活动商品（直减类必填活动价；满减类可空）")
    private List<AdminPromotionItemDTO> items;
    @Schema(description = "满减阶梯（满减类必填）")
    private List<AdminPromotionRuleDTO> rules;
}
