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
 * 我的优惠券。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCouponVO {
    private Long userCouponId;
    private Long templateId;
    private String name;
    private String type;
    private String typeDesc;
    private BigDecimal faceValue;
    private BigDecimal discountRate;
    private BigDecimal maxDiscount;
    private BigDecimal thresholdAmount;
    private String scopeType;
    private String targetGroup;
    @Schema(description = "状态：AVAILABLE / LOCKED / USED / EXPIRED")
    private String status;
    private String statusDesc;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime usedTime;
    private Long orderId;
    private String description;
}
