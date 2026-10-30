package ecommerce.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台新建 / 编辑优惠券请求。
 */
@Data
public class AdminCouponDTO {
    @Schema(description = "券名称")
    private String name;
    @Schema(description = "类型：FIXED 满减 / DISCOUNT 折扣")
    private String type;
    @Schema(description = "满减金额（满减券）")
    private BigDecimal faceValue;
    @Schema(description = "折扣率（折扣券，0.85 = 85 折）")
    private BigDecimal discountRate;
    @Schema(description = "折扣券最高优惠（空=不封顶）")
    private BigDecimal maxDiscount;
    @Schema(description = "使用门槛，0 = 无门槛")
    private BigDecimal thresholdAmount;
    @Schema(description = "适用范围：ALL / CATEGORY / PRODUCT")
    private String scopeType;
    @Schema(description = "目标人群：ALL / NEW_USER")
    private String targetGroup;
    @Schema(description = "最低会员等级 ID，0 = 不限")
    private Long minMemberLevel;
    @Schema(description = "发行总量")
    private Integer totalQuantity;
    @Schema(description = "每人限领（v1 固定 1）")
    private Integer perUserLimit;
    @Schema(description = "领取开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime receiveStart;
    @Schema(description = "领取结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime receiveEnd;
    @Schema(description = "有效期类型：FIXED / RECEIVE_DAYS")
    private String validType;
    @Schema(description = "固定有效期开始")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validStart;
    @Schema(description = "固定有效期结束")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validEnd;
    @Schema(description = "领取后有效天数")
    private Integer validDays;
    @Schema(description = "使用说明")
    private String description;
    @Schema(description = "适用分类 ID（scopeType=CATEGORY）")
    private List<Long> categoryIds;
    @Schema(description = "适用商品 ID（scopeType=PRODUCT）")
    private List<Long> productIds;
}
