package ecommerce.order.service.impl;

import ecommerce.common.promotion.PromotionCalculator;
import ecommerce.common.promotion.PromotionConfig;
import ecommerce.common.promotion.PromotionConstants;
import ecommerce.common.promotion.PromotionRuleConfig;
import ecommerce.common.promotion.QuoteItemInput;
import ecommerce.common.promotion.QuoteResult;
import ecommerce.order.entity.Promotion;
import ecommerce.order.entity.PromotionItem;
import ecommerce.order.entity.PromotionRule;
import ecommerce.order.entity.PromotionUsage;
import ecommerce.order.mapper.OrderMapper;
import ecommerce.order.mapper.PromotionMapper;
import ecommerce.order.service.PromotionPricingService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 订单侧促销算价实现：从共享库读取活动配置，交给 common 的 PromotionCalculator 计算。
 */
@Service
public class PromotionPricingServiceImpl implements PromotionPricingService {

    private final PromotionMapper promotionMapper;
    private final OrderMapper orderMapper;

    public PromotionPricingServiceImpl(PromotionMapper promotionMapper, OrderMapper orderMapper) {
        this.promotionMapper = promotionMapper;
        this.orderMapper = orderMapper;
    }

    @Override
    public QuoteResult quote(List<QuoteItemInput> items, Long userId) {
        return doQuote(items, userId, false);
    }

    @Override
    public QuoteResult quoteDirect(List<QuoteItemInput> items, Long userId) {
        return doQuote(items, userId, true);
    }

    private QuoteResult doQuote(List<QuoteItemInput> items, Long userId, boolean directOnly) {
        boolean newUser = userId != null && orderMapper.countPaidByUser(userId) == 0;
        List<Promotion> active = promotionMapper.listActive(LocalDateTime.now());
        if (active.isEmpty()) {
            return PromotionCalculator.quote(items, List.of(), newUser);
        }
        List<Long> promotionIds = active.stream().map(Promotion::getId).toList();
        Map<Long, List<PromotionItem>> itemsByPromotion = groupItems(
                promotionMapper.listItemsByPromotionIds(promotionIds));
        Map<Long, List<PromotionRule>> rulesByPromotion = groupRules(
                promotionMapper.listRulesByPromotionIds(promotionIds));

        List<PromotionConfig> configs = new ArrayList<>();
        for (Promotion promotion : active) {
            if (directOnly && !PromotionConstants.TYPE_DIRECT.equals(promotion.getType())) {
                continue;
            }
            List<PromotionItem> promoItems = itemsByPromotion.getOrDefault(promotion.getId(), List.of());
            Map<Long, BigDecimal> productPrices = new HashMap<>();
            Set<Long> productIds = new HashSet<>();
            for (PromotionItem item : promoItems) {
                if (item.getProductId() == null) {
                    continue;
                }
                productIds.add(item.getProductId());
                if (item.getPromotionPrice() != null) {
                    productPrices.put(item.getProductId(), item.getPromotionPrice());
                }
            }
            List<PromotionRuleConfig> rules = rulesByPromotion.getOrDefault(promotion.getId(), List.of())
                    .stream()
                    .map(rule -> PromotionRuleConfig.builder()
                            .thresholdAmount(rule.getThresholdAmount())
                            .discountAmount(rule.getDiscountAmount())
                            .discountRate(rule.getDiscountRate())
                            .build())
                    .toList();
            configs.add(PromotionConfig.builder()
                    .id(promotion.getId())
                    .name(promotion.getName())
                    .type(promotion.getType())
                    .ruleType(promotion.getRuleType())
                    .targetGroup(promotion.getTargetGroup())
                    .scopeType(promotion.getScopeType())
                    .tagName(promotion.getTagName())
                    .productPrices(productPrices)
                    .productIds(productIds)
                    .rules(rules)
                    .build());
        }
        return PromotionCalculator.quote(items, configs, newUser);
    }

    @Override
    public void recordUsage(QuoteResult quote, Long orderId, String orderNo, Long userId) {
        if (quote == null || quote.getAppliedPromotions() == null || quote.getAppliedPromotions().isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<PromotionUsage> usages = quote.getAppliedPromotions().stream()
                .filter(p -> p.getPromotionId() != null)
                .filter(p -> p.getDiscountAmount() != null && p.getDiscountAmount().signum() > 0)
                .map(p -> PromotionUsage.builder()
                        .promotionId(p.getPromotionId())
                        .orderId(orderId)
                        .orderNo(orderNo)
                        .userId(userId)
                        .discountAmount(p.getDiscountAmount())
                        .createTime(now)
                        .build())
                .toList();
        if (!usages.isEmpty()) {
            promotionMapper.insertUsages(usages);
        }
    }

    private Map<Long, List<PromotionItem>> groupItems(List<PromotionItem> items) {
        Map<Long, List<PromotionItem>> map = new HashMap<>();
        for (PromotionItem item : items) {
            map.computeIfAbsent(item.getPromotionId(), k -> new ArrayList<>()).add(item);
        }
        return map;
    }

    private Map<Long, List<PromotionRule>> groupRules(List<PromotionRule> rules) {
        Map<Long, List<PromotionRule>> map = new HashMap<>();
        for (PromotionRule rule : rules) {
            map.computeIfAbsent(rule.getPromotionId(), k -> new ArrayList<>()).add(rule);
        }
        return map;
    }
}
