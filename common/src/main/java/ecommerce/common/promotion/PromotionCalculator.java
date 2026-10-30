package ecommerce.common.promotion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 促销算价引擎：纯函数、不依赖数据库，product-service（展示价）与 order-service（下单）共用。
 * <p>
 * 按黑马促销文档的「三层改价」模型实现：
 * <ol>
 *   <li>直减类：命中活动的商品用活动价替换原价；</li>
 *   <li>满减类：按活动范围内「活动后小计」匹配阶梯，只取优惠最大的一个活动，优惠按小计占比分摊到行；</li>
 *   <li>订单级（优惠券）：暂未实现。</li>
 * </ol>
 * 互斥：同一商品命中多个直减活动时取活动价最低的（同价取 id 小的）；满减全局只应用一个。
 * 活动创建时由后台禁止同商品、同类型、时间重叠的活动。
 */
public final class PromotionCalculator {

    private static final BigDecimal ONE = BigDecimal.ONE;

    private PromotionCalculator() {
    }

    /**
     * @param inputs     商品行（原价 + 数量）
     * @param promotions 当前生效的活动配置
     * @param newUser    用户是否「未下单过的新用户」（新用户专享活动用）
     */
    public static QuoteResult quote(List<QuoteItemInput> inputs, List<PromotionConfig> promotions, boolean newUser) {
        List<QuoteItemInput> safeInputs = inputs == null ? List.of() : inputs;
        List<PromotionConfig> eligible = filterEligible(promotions, newUser);

        List<Line> lines = new ArrayList<>();
        for (QuoteItemInput input : safeInputs) {
            if (input != null && input.getProductId() != null) {
                lines.add(new Line(input));
            }
        }

        // 一次报价里同一个活动可能命中多行，用 map 汇总活动总优惠
        Map<Long, AppliedPromotion> applied = new LinkedHashMap<>();
        applyDirect(lines, eligible, applied);
        applyFullReduce(lines, eligible, applied);

        List<QuoteItemResult> items = new ArrayList<>(lines.size());
        BigDecimal originalAmount = BigDecimal.ZERO;
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal payAmount = BigDecimal.ZERO;
        for (Line line : lines) {
            items.add(QuoteItemResult.builder()
                    .productId(line.productId)
                    .quantity(line.quantity)
                    .originalPrice(scale(line.originalPrice))
                    .finalPrice(scale(line.finalPrice))
                    .originalSubTotal(line.originalSubTotal())
                    .subTotal(line.paySubTotal())
                    .discountAmount(scale(line.discount))
                    .couponDiscountAmount(BigDecimal.ZERO)
                    .promotionId(line.directPromotionId)
                    .promotionName(line.directPromotionName)
                    .promotionTag(line.directPromotionTag)
                    .build());
            originalAmount = originalAmount.add(line.originalSubTotal());
            discountAmount = discountAmount.add(line.discount);
            payAmount = payAmount.add(line.paySubTotal());
        }

        return QuoteResult.builder()
                .items(items)
                .originalAmount(scale(originalAmount))
                .promotionDiscount(scale(discountAmount))
                .couponDiscount(BigDecimal.ZERO)
                .discountAmount(scale(discountAmount))
                .payAmount(scale(payAmount))
                .appliedPromotions(new ArrayList<>(applied.values()))
                .build();
    }

    /** 过滤人群不匹配的活动，并按 id 排序保证结果稳定 */
    private static List<PromotionConfig> filterEligible(List<PromotionConfig> promotions, boolean newUser) {
        if (promotions == null || promotions.isEmpty()) {
            return List.of();
        }
        return promotions.stream()
                .filter(Objects::nonNull)
                .filter(p -> !PromotionConstants.TARGET_NEW_USER.equals(p.getTargetGroup()) || newUser)
                .sorted(Comparator.comparing(PromotionConfig::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    // ==================== 第一层：直减类，改商品价格 ====================
    private static void applyDirect(List<Line> lines, List<PromotionConfig> promotions,
                                    Map<Long, AppliedPromotion> applied) {
        for (Line line : lines) {
            PromotionConfig best = null;
            BigDecimal bestPrice = null;
            for (PromotionConfig promo : promotions) {
                if (!PromotionConstants.TYPE_DIRECT.equals(promo.getType())) {
                    continue;
                }
                BigDecimal promoPrice = promo.getProductPrices() == null
                        ? null : promo.getProductPrices().get(line.productId);
                // 价格非法或不低于原价的活动不生效（后台已校验，这里兜底）
                if (promoPrice == null || promoPrice.signum() < 0
                        || promoPrice.compareTo(line.originalPrice) >= 0) {
                    continue;
                }
                if (bestPrice == null || promoPrice.compareTo(bestPrice) < 0) {
                    best = promo;
                    bestPrice = promoPrice;
                }
            }
            if (best == null) {
                continue;
            }
            // discount 统一按「原价小计 - 优惠价小计」累计
            line.discount = line.originalSubTotal()
                    .subtract(scale(bestPrice.multiply(BigDecimal.valueOf(line.quantity))));
            line.finalPrice = bestPrice;
            line.directPromotionId = best.getId();
            line.directPromotionName = best.getName();
            line.directPromotionTag = tagOf(best);
            mergeApplied(applied, best, line.discount);
        }
    }

    // ==================== 第二层：满减类，改商品小计集合 ====================
    private static void applyFullReduce(List<Line> lines, List<PromotionConfig> promotions,
                                        Map<Long, AppliedPromotion> applied) {
        PromotionConfig bestPromo = null;
        BigDecimal bestDiscount = BigDecimal.ZERO;
        List<Line> bestScope = null;

        for (PromotionConfig promo : promotions) {
            if (!PromotionConstants.TYPE_FULL_REDUCE.equals(promo.getType())) {
                continue;
            }
            List<Line> scope = lines.stream().filter(line -> inScope(promo, line)).toList();
            if (scope.isEmpty()) {
                continue;
            }
            BigDecimal scopePay = scope.stream()
                    .map(Line::paySubTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal discount = matchRule(promo, scopePay);
            if (discount.signum() > 0 && discount.compareTo(bestDiscount) > 0) {
                bestPromo = promo;
                bestDiscount = discount;
                bestScope = scope;
            }
        }

        if (bestPromo == null || bestScope == null) {
            return;
        }
        allocate(bestScope, bestDiscount);
        mergeApplied(applied, bestPromo, bestDiscount);
    }

    private static boolean inScope(PromotionConfig promo, Line line) {
        if (PromotionConstants.SCOPE_ALL.equals(promo.getScopeType())) {
            return true;
        }
        return promo.getProductIds() != null && promo.getProductIds().contains(line.productId);
    }

    /** 命中最高一档阶梯：满额减 / 满额折 */
    private static BigDecimal matchRule(PromotionConfig promo, BigDecimal scopePay) {
        if (promo.getRules() == null || promo.getRules().isEmpty() || scopePay.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        List<PromotionRuleConfig> rules = promo.getRules().stream()
                .filter(Objects::nonNull)
                .filter(r -> r.getThresholdAmount() != null)
                .sorted(Comparator.comparing(PromotionRuleConfig::getThresholdAmount).reversed())
                .toList();
        for (PromotionRuleConfig rule : rules) {
            if (scopePay.compareTo(rule.getThresholdAmount()) < 0) {
                continue;
            }
            BigDecimal discount = BigDecimal.ZERO;
            if (PromotionConstants.RULE_FULL_AMOUNT_REDUCE.equals(promo.getRuleType())
                    && rule.getDiscountAmount() != null) {
                discount = rule.getDiscountAmount();
            } else if (PromotionConstants.RULE_FULL_AMOUNT_DISCOUNT.equals(promo.getRuleType())
                    && rule.getDiscountRate() != null) {
                // 满额折：优惠 = 小计 × (1 - 折扣率)
                discount = scale(scopePay.multiply(ONE.subtract(rule.getDiscountRate())));
            }
            if (discount.signum() < 0) {
                return BigDecimal.ZERO;
            }
            // 优惠不能超过小计本身
            return discount.min(scopePay);
        }
        return BigDecimal.ZERO;
    }

    /** 按活动范围内各行小计占比分摊优惠，末行承接舍入残差 */
    private static void allocate(List<Line> scope, BigDecimal totalDiscount) {
        BigDecimal scopePay = scope.stream()
                .map(Line::paySubTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (scopePay.signum() <= 0) {
            return;
        }
        BigDecimal remaining = totalDiscount;
        for (int i = 0; i < scope.size(); i++) {
            Line line = scope.get(i);
            BigDecimal share;
            if (i == scope.size() - 1) {
                share = remaining;
            } else {
                share = totalDiscount.multiply(line.paySubTotal())
                        .divide(scopePay, 2, RoundingMode.HALF_UP);
            }
            // 单行优惠不能超过该行优惠后小计
            share = share.max(BigDecimal.ZERO).min(line.paySubTotal());
            line.discount = line.discount.add(share);
            remaining = remaining.subtract(share);
        }
    }

    private static void mergeApplied(Map<Long, AppliedPromotion> applied, PromotionConfig promo, BigDecimal discount) {
        applied.compute(promo.getId(), (id, existing) -> {
            if (existing == null) {
                return AppliedPromotion.builder()
                        .promotionId(promo.getId())
                        .promotionName(promo.getName())
                        .type(promo.getType())
                        .discountAmount(scale(discount))
                        .build();
            }
            existing.setDiscountAmount(scale(existing.getDiscountAmount().add(discount)));
            return existing;
        });
    }

    private static String tagOf(PromotionConfig promo) {
        if (promo.getTagName() != null && !promo.getTagName().isBlank()) {
            return promo.getTagName();
        }
        return PromotionConstants.RULE_NEW_USER.equals(promo.getRuleType()) ? "新人专享" : "限时折扣";
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    /** 内部计算行，discount 始终表示「相对原价的总优惠」 */
    private static final class Line {
        private final Long productId;
        private final int quantity;
        private final BigDecimal originalPrice;
        private BigDecimal finalPrice;
        private BigDecimal discount = BigDecimal.ZERO;
        private Long directPromotionId;
        private String directPromotionName;
        private String directPromotionTag;

        private Line(QuoteItemInput input) {
            this.productId = input.getProductId();
            this.quantity = input.getQuantity() == null || input.getQuantity() < 1 ? 1 : input.getQuantity();
            this.originalPrice = input.getOriginalPrice() == null || input.getOriginalPrice().signum() < 0
                    ? BigDecimal.ZERO : input.getOriginalPrice();
            this.finalPrice = this.originalPrice;
        }

        private BigDecimal originalSubTotal() {
            return scale(originalPrice.multiply(BigDecimal.valueOf(quantity)));
        }

        /** 优惠后小计（不含保险），最低 0 */
        private BigDecimal paySubTotal() {
            BigDecimal pay = originalSubTotal().subtract(discount);
            return pay.signum() < 0 ? BigDecimal.ZERO : scale(pay);
        }
    }
}
