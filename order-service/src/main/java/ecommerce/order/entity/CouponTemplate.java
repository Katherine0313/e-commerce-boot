package ecommerce.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板（coupon_template 表）。
 * <p>
 * 状态不落库：未开始 / 发放中 / 已结束由领取时间和剩余量推导，manualStatus=1 表示停止发放。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponTemplate {
    private Long id;
    private String name;
    /** FIXED 满减 / DISCOUNT 折扣 */
    private String type;
    private BigDecimal faceValue;
    private BigDecimal discountRate;
    private BigDecimal maxDiscount;
    /** 使用门槛，0 = 无门槛 */
    private BigDecimal thresholdAmount;
    /** ALL / CATEGORY / PRODUCT */
    private String scopeType;
    /** ALL / NEW_USER */
    private String targetGroup;
    /** 最低会员等级 ID，0 = 不限 */
    private Long minMemberLevel;
    private Integer totalQuantity;
    private Integer remainQuantity;
    private Integer perUserLimit;
    private LocalDateTime receiveStart;
    private LocalDateTime receiveEnd;
    /** FIXED / RECEIVE_DAYS */
    private String validType;
    private LocalDateTime validStart;
    private LocalDateTime validEnd;
    private Integer validDays;
    private Integer manualStatus;
    private Integer isDeleted;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
