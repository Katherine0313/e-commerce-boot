package ecommerce.user.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 后台会员列表行。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminMemberVO {
    private Long id;
    private String name;
    private String phone;
    private String avatar;
    private Long memberLevelId;
    private String levelName;
    @Schema(description = "成长值")
    private Integer growthValue;
    @Schema(description = "累计实付（已扣退款）")
    private BigDecimal totalPaidAmount;
    @Schema(description = "已支付订单数")
    private Integer paidOrderCount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
