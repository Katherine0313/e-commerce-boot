package ecommerce.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 促销活动实体（promotion 表）。
 * <p>
 * 活动状态不落库：未开始 / 进行中 / 已结束由 startTime、endTime 推导，
 * manualStatus=1 表示运营提前结束。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Promotion {
    private Long id;
    private String name;
    /** DIRECT 直减 / FULL_REDUCE 满减 */
    private String type;
    /** DISCOUNT / NEW_USER / FULL_AMOUNT_REDUCE / FULL_AMOUNT_DISCOUNT */
    private String ruleType;
    /** ALL / NEW_USER */
    private String targetGroup;
    /** ALL / PRODUCT */
    private String scopeType;
    private String tagName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer manualStatus;
    private Integer isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
