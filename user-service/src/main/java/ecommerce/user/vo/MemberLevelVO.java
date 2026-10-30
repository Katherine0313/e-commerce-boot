package ecommerce.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会员等级展示。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberLevelVO {
    private Long id;
    private String name;
    @Schema(description = "成长值门槛")
    private Integer growthThreshold;
    @Schema(description = "权益文案")
    private String benefits;
    private Integer sortOrder;
}
