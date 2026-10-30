package ecommerce.order.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台优惠券 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCouponVO {
    private Long id;
    private String name;
    private String type;
    private String typeDesc;
    private BigDecimal faceValue;
    private BigDecimal discountRate;
    private BigDecimal maxDiscount;
    private BigDecimal thresholdAmount;
    private String scopeType;
    private String scopeTypeDesc;
    private String targetGroup;
    private String targetGroupDesc;
    @Schema(description = "最低会员等级 ID，0 = 不限")
    private Long minMemberLevel;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime receiveStart;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime receiveEnd;
    private String validType;
    private String validTypeDesc;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validStart;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validEnd;
    private Integer validDays;
    private Integer totalQuantity;
    private Integer remainQuantity;
    private Integer perUserLimit;
    private String description;
    @Schema(description = "状态：PENDING / ACTIVE / ENDED / STOPPED")
    private String status;
    private String statusDesc;
    @Schema(description = "已领取数量")
    private Long issuedCount;
    @Schema(description = "已使用数量")
    private Long usedCount;
    @Schema(description = "累计优惠金额")
    private BigDecimal usedDiscount;
    private List<Long> categoryIds;
    private List<Long> productIds;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
