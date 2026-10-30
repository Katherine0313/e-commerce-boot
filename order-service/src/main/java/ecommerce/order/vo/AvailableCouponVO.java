package ecommerce.order.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 确认页可用券：后端按当前商品算好每张券能减多少。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableCouponVO {
    private Long userCouponId;
    private Long templateId;
    private String name;
    private String type;
    private BigDecimal thresholdAmount;
    private BigDecimal discountAmount;
    @Schema(description = "当前订单是否可用")
    private Boolean usable;
    @Schema(description = "不可用原因（可用时为空）")
    private String reason;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
