package ecommerce.common.promotion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一条促销活动的算价配置（从 promotion / promotion_item / promotion_rule 组装）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionConfig {
    private Long id;
    private String name;
    /** DIRECT / FULL_REDUCE */
    private String type;
    /** DISCOUNT / NEW_USER / FULL_AMOUNT_REDUCE / FULL_AMOUNT_DISCOUNT */
    private String ruleType;
    /** ALL / NEW_USER */
    private String targetGroup;
    /** ALL / PRODUCT */
    private String scopeType;
    /** C 端展示标签 */
    private String tagName;
    /** 指定商品的活动价：productId -> 活动价（直减类用） */
    private Map<Long, BigDecimal> productPrices;
    /** 活动覆盖的商品 id（满减类判断范围用；scopeType=ALL 时忽略） */
    private Set<Long> productIds;
    /** 满减阶梯 */
    private List<PromotionRuleConfig> rules;
}
