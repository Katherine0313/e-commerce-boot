package ecommerce.order.service.impl;

import ecommerce.common.promotion.QuoteItemInput;
import ecommerce.common.promotion.QuoteItemResult;
import ecommerce.common.promotion.QuoteResult;
import ecommerce.exception.BusinessException;
import ecommerce.order.entity.CouponScope;
import ecommerce.order.entity.CouponTemplate;
import ecommerce.order.entity.UserCoupon;
import ecommerce.order.mapper.CouponMapper;
import ecommerce.order.mapper.MemberMapper;
import ecommerce.order.mapper.OrderMapper;
import ecommerce.order.service.CouponPricingService;
import ecommerce.order.service.PromotionPricingService;
import ecommerce.order.support.CouponConstants;
import ecommerce.order.vo.AvailableCouponVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 券算价实现：券作用在「促销优惠后商品小计」之上，按适用商品占比分摊到行。
 * <p>
 * 门槛按促销后金额判断；保险不参与任何优惠；一笔订单最多一张券。
 */
@Service
public class CouponPricingServiceImpl implements CouponPricingService {

    private final CouponMapper couponMapper;
    private final OrderMapper orderMapper;
    private final MemberMapper memberMapper;
    private final PromotionPricingService promotionPricingService;

    public CouponPricingServiceImpl(CouponMapper couponMapper, OrderMapper orderMapper,
                                    MemberMapper memberMapper,
                                    PromotionPricingService promotionPricingService) {
        this.couponMapper = couponMapper;
        this.orderMapper = orderMapper;
        this.memberMapper = memberMapper;
        this.promotionPricingService = promotionPricingService;
    }

    @Override
    public QuoteResult applyCoupon(QuoteResult promotionQuote, List<QuoteItemInput> inputs,
                                   Long userCouponId, Long userId) {
        if (userCouponId == null) {
            return promotionQuote;
        }
        UserCoupon userCoupon = couponMapper.findUserCouponById(userCouponId);
        if (userCoupon == null || !Objects.equals(userCoupon.getUserId(), userId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "优惠券不存在");
        }
        CouponTemplate template = couponMapper.findTemplateById(userCoupon.getTemplateId());
        if (template == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "优惠券已失效");
        }
        QuoteResult copy = copyQuote(promotionQuote);
        String reason = validateAndApply(copy, userCoupon, template, isNewUser(userId),
                currentMemberLevel(userId));
        if (reason != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, reason);
        }
        return copy;
    }

    @Override
    public List<AvailableCouponVO> listAvailable(List<QuoteItemInput> inputs, Long userId) {
        if (userId == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<UserCoupon> userCoupons = couponMapper.listAvailableUserCoupons(userId, now);
        if (userCoupons.isEmpty()) {
            return List.of();
        }
        QuoteResult baseQuote = promotionPricingService.quote(inputs, userId);
        boolean newUser = isNewUser(userId);
        long memberLevel = currentMemberLevel(userId);
        List<AvailableCouponVO> result = new ArrayList<>();
        for (UserCoupon userCoupon : userCoupons) {
            CouponTemplate template = couponMapper.findTemplateById(userCoupon.getTemplateId());
            if (template == null) {
                continue;
            }
            QuoteResult copy = copyQuote(baseQuote);
            String reason = validateAndApply(copy, userCoupon, template, newUser, memberLevel);
            BigDecimal discount = reason == null ? copy.getCouponDiscount() : BigDecimal.ZERO;
            result.add(AvailableCouponVO.builder()
                    .userCouponId(userCoupon.getId())
                    .templateId(template.getId())
                    .name(template.getName())
                    .type(template.getType())
                    .thresholdAmount(template.getThresholdAmount())
                    .discountAmount(discount)
                    .usable(reason == null)
                    .reason(reason)
                    .endTime(userCoupon.getEndTime())
                    .build());
        }
        // 可用在前，优惠大的在前
        result.sort(Comparator
                .comparing((AvailableCouponVO c) -> Boolean.TRUE.equals(c.getUsable()) ? 0 : 1)
                .thenComparing(c -> c.getDiscountAmount() == null ? BigDecimal.ZERO : c.getDiscountAmount(),
                        Comparator.reverseOrder()));
        return result;
    }

    // ==================== 内部方法 ====================

    /**
     * 校验并把券优惠写入报价。
     *
     * @return null 表示可用；非 null 为不可用原因
     */
    private String validateAndApply(QuoteResult quote, UserCoupon userCoupon, CouponTemplate template,
                                    boolean newUser, long memberLevel) {
        LocalDateTime now = LocalDateTime.now();
        if (!CouponConstants.USER_UNUSED.equals(userCoupon.getStatus())) {
            return "优惠券状态不可用";
        }
        if (userCoupon.getStartTime() != null && now.isBefore(userCoupon.getStartTime())) {
            return "优惠券尚未生效";
        }
        if (userCoupon.getEndTime() != null && !now.isBefore(userCoupon.getEndTime())) {
            return "优惠券已过期";
        }
        if (CouponConstants.TARGET_NEW_USER.equals(template.getTargetGroup()) && !newUser) {
            return "仅限新用户使用";
        }
        long minLevel = template.getMinMemberLevel() == null ? 0 : template.getMinMemberLevel();
        if (minLevel > 0 && memberLevel < minLevel) {
            return "会员等级不足";
        }

        Set<Long> productIds = new HashSet<>();
        Set<Long> categoryIds = new HashSet<>();
        for (CouponScope scope : couponMapper.listScopes(template.getId())) {
            if (CouponConstants.SCOPE_PRODUCT.equals(scope.getTargetType())) {
                productIds.add(scope.getTargetId());
            } else if (CouponConstants.SCOPE_CATEGORY.equals(scope.getTargetType())) {
                categoryIds.add(scope.getTargetId());
            }
        }
        Map<Long, ProductCategory> categories = loadProductCategories(quote.getItems());

        List<QuoteItemResult> eligible = new ArrayList<>();
        BigDecimal eligibleAmount = BigDecimal.ZERO;
        for (QuoteItemResult item : quote.getItems()) {
            if (!inScope(template, item, productIds, categoryIds, categories)) {
                continue;
            }
            eligible.add(item);
            eligibleAmount = eligibleAmount.add(item.getSubTotal());
        }
        if (eligible.isEmpty()) {
            return "不适用于当前商品";
        }
        BigDecimal threshold = template.getThresholdAmount() == null
                ? BigDecimal.ZERO : template.getThresholdAmount();
        if (eligibleAmount.compareTo(threshold) < 0) {
            return "还差 ¥" + scale(threshold.subtract(eligibleAmount)) + " 可用";
        }

        BigDecimal discount = computeDiscount(template, eligibleAmount);
        if (discount.signum() <= 0) {
            return "优惠金额为 0";
        }

        BigDecimal promotionPay = quote.getPayAmount() == null ? BigDecimal.ZERO : quote.getPayAmount();
        allocate(eligible, discount);

        quote.setCouponDiscount(scale(discount));
        quote.setCouponId(userCoupon.getId());
        quote.setCouponName(template.getName());
        BigDecimal promotionDiscount = quote.getPromotionDiscount() == null
                ? BigDecimal.ZERO : quote.getPromotionDiscount();
        quote.setDiscountAmount(scale(promotionDiscount.add(discount)));
        quote.setPayAmount(scale(promotionPay.subtract(discount)));
        return null;
    }

    private boolean inScope(CouponTemplate template, QuoteItemResult item, Set<Long> productIds,
                            Set<Long> categoryIds, Map<Long, ProductCategory> categories) {
        if (CouponConstants.SCOPE_ALL.equals(template.getScopeType())) {
            return true;
        }
        if (CouponConstants.SCOPE_PRODUCT.equals(template.getScopeType())) {
            return productIds.contains(item.getProductId());
        }
        // 分类券：商品所属分类或其父分类命中即可（后台选一级分类时包含二级商品）
        ProductCategory category = categories.get(item.getProductId());
        if (category == null) {
            return false;
        }
        return (category.categoryId() != null && categoryIds.contains(category.categoryId()))
                || (category.parentId() != null && categoryIds.contains(category.parentId()));
    }

    private BigDecimal computeDiscount(CouponTemplate template, BigDecimal eligibleAmount) {
        BigDecimal discount;
        if (CouponConstants.TYPE_DISCOUNT.equals(template.getType())) {
            BigDecimal rate = template.getDiscountRate() == null ? BigDecimal.ONE : template.getDiscountRate();
            discount = scale(eligibleAmount.multiply(BigDecimal.ONE.subtract(rate)));
            if (template.getMaxDiscount() != null && template.getMaxDiscount().signum() > 0) {
                discount = discount.min(template.getMaxDiscount());
            }
        } else {
            discount = template.getFaceValue() == null ? BigDecimal.ZERO : template.getFaceValue();
        }
        discount = discount.min(eligibleAmount);
        return discount.signum() < 0 ? BigDecimal.ZERO : scale(discount);
    }

    /** 券优惠按适用商品小计占比分摊到行，末行承接舍入残差 */
    private void allocate(List<QuoteItemResult> scope, BigDecimal totalDiscount) {
        BigDecimal scopeAmount = scope.stream()
                .map(QuoteItemResult::getSubTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (scopeAmount.signum() <= 0) {
            return;
        }
        BigDecimal remaining = totalDiscount;
        for (int i = 0; i < scope.size(); i++) {
            QuoteItemResult item = scope.get(i);
            BigDecimal share;
            if (i == scope.size() - 1) {
                share = remaining;
            } else {
                share = totalDiscount.multiply(item.getSubTotal())
                        .divide(scopeAmount, 2, RoundingMode.HALF_UP);
            }
            share = share.max(BigDecimal.ZERO).min(item.getSubTotal());
            item.setCouponDiscountAmount(share);
            item.setDiscountAmount((item.getDiscountAmount() == null ? BigDecimal.ZERO : item.getDiscountAmount())
                    .add(share));
            item.setSubTotal(item.getSubTotal().subtract(share));
            remaining = remaining.subtract(share);
        }
    }

    private Map<Long, ProductCategory> loadProductCategories(List<QuoteItemResult> items) {
        List<Long> productIds = items.stream()
                .map(QuoteItemResult::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, ProductCategory> map = new HashMap<>();
        if (productIds.isEmpty()) {
            return map;
        }
        for (Map<String, Object> row : couponMapper.listProductCategories(productIds)) {
            Long productId = toLong(row.get("product_id"));
            if (productId != null) {
                map.put(productId, new ProductCategory(toLong(row.get("category_id")), toLong(row.get("parent_id"))));
            }
        }
        return map;
    }

    private boolean isNewUser(Long userId) {
        return userId != null && orderMapper.countPaidByUser(userId) == 0;
    }

    private long currentMemberLevel(Long userId) {
        Long levelId = userId == null ? null : memberMapper.findMemberLevelId(userId);
        return levelId == null ? 1L : levelId;
    }

    /** 深拷贝报价，避免给列表里每张券试算时污染原报价 */
    private QuoteResult copyQuote(QuoteResult source) {
        List<QuoteItemResult> items = new ArrayList<>();
        if (source.getItems() != null) {
            for (QuoteItemResult item : source.getItems()) {
                items.add(QuoteItemResult.builder()
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .originalPrice(item.getOriginalPrice())
                        .finalPrice(item.getFinalPrice())
                        .originalSubTotal(item.getOriginalSubTotal())
                        .subTotal(item.getSubTotal())
                        .discountAmount(item.getDiscountAmount())
                        .couponDiscountAmount(item.getCouponDiscountAmount())
                        .promotionId(item.getPromotionId())
                        .promotionName(item.getPromotionName())
                        .promotionTag(item.getPromotionTag())
                        .build());
            }
        }
        return QuoteResult.builder()
                .items(items)
                .originalAmount(source.getOriginalAmount())
                .promotionDiscount(source.getPromotionDiscount())
                .couponDiscount(source.getCouponDiscount())
                .discountAmount(source.getDiscountAmount())
                .payAmount(source.getPayAmount())
                .couponId(source.getCouponId())
                .couponName(source.getCouponName())
                .appliedPromotions(source.getAppliedPromotions())
                .build();
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private record ProductCategory(Long categoryId, Long parentId) {
    }
}
