package ecommerce.product.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台促销活动 VO（列表 / 详情 / 编辑回填）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPromotionVO {
    @Schema(description = "活动 ID")
    private Long id;
    @Schema(description = "活动名称")
    private String name;
    @Schema(description = "类型 code")
    private String type;
    @Schema(description = "类型中文")
    private String typeDesc;
    @Schema(description = "玩法 code")
    private String ruleType;
    @Schema(description = "玩法中文")
    private String ruleTypeDesc;
    @Schema(description = "目标人群 code")
    private String targetGroup;
    @Schema(description = "目标人群中文")
    private String targetGroupDesc;
    @Schema(description = "商品范围 code")
    private String scopeType;
    @Schema(description = "商品范围中文")
    private String scopeTypeDesc;
    @Schema(description = "C 端促销标签")
    private String tagName;
    @Schema(description = "开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @Schema(description = "结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    @Schema(description = "状态 code：PENDING / ACTIVE / ENDED / CANCELLED")
    private String status;
    @Schema(description = "状态中文")
    private String statusDesc;
    @Schema(description = "活动商品数")
    private Integer productCount;
    @Schema(description = "该活动产生过的订单数（>0 表示已产生订单，不能删除，只能提前结束）")
    private Integer orderCount;
    @Schema(description = "活动商品（详情返回）")
    private List<PromotionItemVO> items;
    @Schema(description = "满减阶梯（详情返回）")
    private List<PromotionRuleVO> rules;
    @Schema(description = "活动效果（详情返回）")
    private PromotionStatsVO stats;
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
