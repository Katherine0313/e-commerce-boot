package ecommerce.order.service.impl;

import ecommerce.common.promotion.QuoteItemInput;
import ecommerce.exception.BusinessException;
import ecommerce.order.dto.OrderItemDTO;
import ecommerce.order.entity.CouponTemplate;
import ecommerce.order.entity.Product;
import ecommerce.order.entity.UserCoupon;
import ecommerce.order.mapper.CouponMapper;
import ecommerce.order.mapper.MemberMapper;
import ecommerce.order.mapper.OrderMapper;
import ecommerce.order.mapper.ProductMapper;
import ecommerce.order.service.CouponPricingService;
import ecommerce.order.service.CouponService;
import ecommerce.order.support.CouponConstants;
import ecommerce.order.vo.AvailableCouponVO;
import ecommerce.order.vo.CouponCenterVO;
import ecommerce.order.vo.ProductSubsidyVO;
import ecommerce.order.vo.UserCouponVO;
import ecommerce.utils.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * C 端优惠券实现：领券中心 / 领券 / 我的券。
 */
@Service
public class CouponServiceImpl implements CouponService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter CODE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final CouponMapper couponMapper;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final MemberMapper memberMapper;
    private final CouponPricingService couponPricingService;

    public CouponServiceImpl(CouponMapper couponMapper, ProductMapper productMapper, OrderMapper orderMapper,
                             MemberMapper memberMapper, CouponPricingService couponPricingService) {
        this.couponMapper = couponMapper;
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.memberMapper = memberMapper;
        this.couponPricingService = couponPricingService;
    }

    @Override
    public List<CouponCenterVO> center() {
        LocalDateTime now = LocalDateTime.now();
        Long userId = currentUserId();
        Set<Long> claimed = userId == null
                ? Set.of() : new HashSet<>(couponMapper.listClaimedTemplateIds(userId));
        boolean newUser = isNewUser(userId);
        long memberLevel = currentMemberLevel(userId);
        return couponMapper.listCenter(now).stream()
                // 新用户专享券不展示给老用户；会员专享券不展示给等级不足用户
                .filter(t -> !CouponConstants.TARGET_NEW_USER.equals(t.getTargetGroup()) || newUser)
                .filter(t -> t.getMinMemberLevel() == null || t.getMinMemberLevel() <= 0
                        || memberLevel >= t.getMinMemberLevel())
                .map(t -> CouponCenterVO.builder()
                        .templateId(t.getId())
                        .name(t.getName())
                        .type(t.getType())
                        .typeDesc(typeDesc(t.getType()))
                        .faceValue(t.getFaceValue())
                        .discountRate(t.getDiscountRate())
                        .maxDiscount(t.getMaxDiscount())
                        .thresholdAmount(t.getThresholdAmount())
                        .scopeTypeDesc(scopeDesc(t.getScopeType()))
                        .targetGroupDesc(targetDesc(t.getTargetGroup()))
                        .minMemberLevel(t.getMinMemberLevel())
                        .validityDesc(validityDesc(t))
                        .remainQuantity(t.getRemainQuantity())
                        .claimed(claimed.contains(t.getId()))
                        .description(t.getDescription())
                        .build())
                .toList();
    }

    @Override
    public ProductSubsidyVO productSubsidy(Long productId) {
        if (productId == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        CouponTemplate template = couponMapper.findProductSubsidy(productId, now);
        if (template == null) {
            return null;
        }
        Long userId = currentUserId();
        boolean claimed = userId != null
                && couponMapper.findUserCouponByUserAndTemplate(userId, template.getId()) != null;
        return ProductSubsidyVO.builder()
                .templateId(template.getId())
                .name(template.getName())
                .discountRate(template.getDiscountRate())
                .discountDesc(discountDesc(template))
                .maxDiscount(template.getMaxDiscount())
                .thresholdAmount(template.getThresholdAmount())
                .remainQuantity(template.getRemainQuantity())
                .claimed(claimed)
                .receiveEnd(template.getReceiveEnd())
                .description(template.getDescription())
                .build();
    }

    @Override
    @Transactional
    public UserCouponVO claim(Long templateId) {
        Long userId = currentUserId();
        CouponTemplate template = couponMapper.findTemplateById(templateId);
        LocalDateTime now = LocalDateTime.now();
        if (template == null || template.getManualStatus() == 1
                || now.isBefore(template.getReceiveStart()) || !now.isBefore(template.getReceiveEnd())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "优惠券不在领取时间内");
        }
        if (CouponConstants.TARGET_NEW_USER.equals(template.getTargetGroup()) && !isNewUser(userId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该优惠券仅限新用户领取");
        }
        if (template.getMinMemberLevel() != null && template.getMinMemberLevel() > 0
                && currentMemberLevel(userId) < template.getMinMemberLevel()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "会员等级不足，暂不能领取");
        }
        if (template.getRemainQuantity() == null || template.getRemainQuantity() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "优惠券已领完");
        }
        if (couponMapper.findUserCouponByUserAndTemplate(userId, templateId) != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "您已领取过该优惠券");
        }
        UserCoupon userCoupon = buildUserCoupon(template, userId, CouponConstants.SOURCE_RECEIVE, now);
        try {
            couponMapper.insertUserCoupon(userCoupon);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发重复领取：唯一键兜底，返回友好提示
            throw new BusinessException(HttpStatus.BAD_REQUEST, "您已领取过该优惠券");
        }
        // 扣减失败（领完）抛错回滚插入
        if (couponMapper.decreaseRemain(templateId) == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "优惠券已领完");
        }
        return toUserCouponVO(userCoupon, template, now);
    }

    @Override
    public List<UserCouponVO> myCoupons(String status) {
        Long userId = currentUserId();
        LocalDateTime now = LocalDateTime.now();
        List<UserCoupon> coupons = couponMapper.listUserCoupons(userId);
        return coupons.stream()
                .map(uc -> {
                    CouponTemplate template = couponMapper.findTemplateById(uc.getTemplateId());
                    return template == null ? null : toUserCouponVO(uc, template, now);
                })
                .filter(vo -> vo != null)
                .filter(vo -> status == null || status.isBlank() || status.equalsIgnoreCase(vo.getStatus()))
                .toList();
    }

    @Override
    public List<AvailableCouponVO> available(List<OrderItemDTO> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "商品明细不能为空");
        }
        List<QuoteItemInput> inputs = new java.util.ArrayList<>();
        for (OrderItemDTO item : items) {
            if (item == null || item.getProductId() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "商品 ID 不能为空");
            }
            Product product = productMapper.findById(item.getProductId());
            if (product == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "商品 ID " + item.getProductId() + " 不存在");
            }
            inputs.add(QuoteItemInput.builder()
                    .productId(product.getId())
                    .quantity(item.getQuantity() == null ? 1 : Math.max(1, item.getQuantity()))
                    .originalPrice(product.getSalesPrice() == null ? BigDecimal.ZERO : product.getSalesPrice())
                    .build());
        }
        return couponPricingService.listAvailable(inputs, currentUserId());
    }

    // ==================== 内部方法 ====================

    private UserCoupon buildUserCoupon(CouponTemplate template, Long userId, String source, LocalDateTime now) {
        LocalDateTime start;
        LocalDateTime end;
        if (CouponConstants.VALID_RECEIVE_DAYS.equals(template.getValidType())) {
            start = now;
            end = now.plusDays(template.getValidDays() == null ? 0 : template.getValidDays());
        } else {
            start = template.getValidStart();
            end = template.getValidEnd();
        }
        return UserCoupon.builder()
                .code(generateCode())
                .templateId(template.getId())
                .userId(userId)
                .status(CouponConstants.USER_UNUSED)
                .receiveTime(now)
                .startTime(start)
                .endTime(end)
                .source(source)
                .build();
    }

    private UserCouponVO toUserCouponVO(UserCoupon coupon, CouponTemplate template, LocalDateTime now) {
        String status = userCouponStatus(coupon, now);
        return UserCouponVO.builder()
                .userCouponId(coupon.getId())
                .templateId(template.getId())
                .name(template.getName())
                .type(template.getType())
                .typeDesc(typeDesc(template.getType()))
                .faceValue(template.getFaceValue())
                .discountRate(template.getDiscountRate())
                .maxDiscount(template.getMaxDiscount())
                .thresholdAmount(template.getThresholdAmount())
                .scopeType(template.getScopeType())
                .targetGroup(template.getTargetGroup())
                .status(status)
                .statusDesc(userCouponStatusDesc(status))
                .startTime(coupon.getStartTime())
                .endTime(coupon.getEndTime())
                .usedTime(coupon.getUsedTime())
                .orderId(coupon.getOrderId())
                .description(template.getDescription())
                .build();
    }

    /** 用户券展示状态：UNUSED 再按时间细分未生效/可用/已过期 */
    private String userCouponStatus(UserCoupon coupon, LocalDateTime now) {
        if (CouponConstants.USER_USED.equals(coupon.getStatus())) {
            return "USED";
        }
        if (CouponConstants.USER_LOCKED.equals(coupon.getStatus())) {
            return "LOCKED";
        }
        if (coupon.getStartTime() != null && now.isBefore(coupon.getStartTime())) {
            return "NOT_STARTED";
        }
        if (coupon.getEndTime() != null && !now.isBefore(coupon.getEndTime())) {
            return "EXPIRED";
        }
        return "AVAILABLE";
    }

    private String userCouponStatusDesc(String status) {
        return switch (status) {
            case "USED" -> "已使用";
            case "LOCKED" -> "使用中";
            case "NOT_STARTED" -> "未生效";
            case "EXPIRED" -> "已过期";
            default -> "可使用";
        };
    }

    private String typeDesc(String type) {
        return CouponConstants.TYPE_DISCOUNT.equals(type) ? "折扣券" : "满减券";
    }

    /** 补贴横幅展示文案：折扣券按「立减 x%」换算，满减券按面额 */
    private String discountDesc(CouponTemplate template) {
        if (!CouponConstants.TYPE_DISCOUNT.equals(template.getType())) {
            return "立减" + (template.getFaceValue() == null ? "0" : template.getFaceValue().stripTrailingZeros().toPlainString()) + "元";
        }
        BigDecimal rate = template.getDiscountRate() == null ? BigDecimal.ONE : template.getDiscountRate();
        // 兼容后台把折扣率存成 90 而不是 0.9 的历史数据
        if (rate.compareTo(BigDecimal.ONE) > 0) {
            rate = rate.divide(BigDecimal.valueOf(100));
        }
        BigDecimal off = BigDecimal.ONE.subtract(rate)
                .multiply(BigDecimal.valueOf(100))
                .stripTrailingZeros();
        return "立减" + off.toPlainString() + "%";
    }

    private String scopeDesc(String scope) {
        return switch (scope == null ? "" : scope) {
            case CouponConstants.SCOPE_CATEGORY -> "指定分类";
            case CouponConstants.SCOPE_PRODUCT -> "指定商品";
            default -> "全场通用";
        };
    }

    private String targetDesc(String target) {
        return CouponConstants.TARGET_NEW_USER.equals(target) ? "新用户专享" : "全部用户";
    }

    private String validityDesc(CouponTemplate template) {
        if (CouponConstants.VALID_RECEIVE_DAYS.equals(template.getValidType())) {
            return "领取后 " + (template.getValidDays() == null ? 0 : template.getValidDays()) + " 天内有效";
        }
        return "有效期 " + formatDate(template.getValidStart()) + " ~ " + formatDate(template.getValidEnd());
    }

    private String formatDate(LocalDateTime time) {
        return time == null ? "-" : time.format(DATE_FMT);
    }

    private String generateCode() {
        return "CP" + LocalDateTime.now().format(CODE_FMT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private boolean isNewUser(Long userId) {
        return userId != null && orderMapper.countPaidByUser(userId) == 0;
    }

    /** 用户当前会员等级；未初始化按普通会员 1 处理 */
    private long currentMemberLevel(Long userId) {
        Long levelId = userId == null ? null : memberMapper.findMemberLevelId(userId);
        return levelId == null ? 1L : levelId;
    }

    private Long currentUserId() {
        return UserContext.getUserId();
    }
}
