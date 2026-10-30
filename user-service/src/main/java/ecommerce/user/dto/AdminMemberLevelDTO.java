package ecommerce.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 后台编辑会员等级请求。
 */
@Data
public class AdminMemberLevelDTO {
    @Schema(description = "等级名称")
    private String name;
    @Schema(description = "成长值门槛")
    private Integer growthThreshold;
    @Schema(description = "权益文案")
    private String benefits;
    @Schema(description = "排序值")
    private Integer sortOrder;
}
