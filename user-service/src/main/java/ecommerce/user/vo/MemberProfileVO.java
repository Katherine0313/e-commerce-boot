package ecommerce.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 会员中心：当前等级、成长值、下一等级进度。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberProfileVO {
    private Long userId;
    private String name;
    private String avatar;
    private Long levelId;
    private String levelName;
    @Schema(description = "当前成长值")
    private Integer growthValue;
    @Schema(description = "当前等级权益文案")
    private String benefits;
    @Schema(description = "累计实付（已扣退款）")
    private BigDecimal totalPaidAmount;
    @Schema(description = "已支付订单数")
    private Integer paidOrderCount;
    @Schema(description = "当前等级成长值门槛")
    private Integer currentThreshold;
    @Schema(description = "下一等级 ID（已满级为 null）")
    private Long nextLevelId;
    private String nextLevelName;
    private Integer nextLevelThreshold;
    @Schema(description = "距下一等级还差多少成长值（已满级为 0）")
    private Integer growthToNext;
}
