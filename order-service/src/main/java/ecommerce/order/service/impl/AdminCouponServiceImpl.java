package ecommerce.order.service.impl;

import ecommerce.common.PageVO;
import ecommerce.exception.BusinessException;
import ecommerce.order.dto.AdminCouponDTO;
import ecommerce.order.dto.AdminCouponGrantDTO;
import ecommerce.order.entity.CouponScope;
import ecommerce.order.entity.CouponTemplate;
import ecommerce.order.entity.UserCoupon;
import ecommerce.order.mapper.CouponMapper;
import ecommerce.order.service.AdminCouponService;
import ecommerce.order.support.CouponConstants;
import ecommerce.order.vo.AdminCouponVO;
import ecommerce.order.vo.CouponGrantResultVO;
import ecommerce.order.vo.CouponRecordVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 后台优惠券管理实现。
 */
@Service
public class AdminCouponServiceImpl implements AdminCouponService {

    private static final DateTimeFormatter CODE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final CouponMapper couponMapper;

    public AdminCouponServiceImpl(CouponMapper couponMapper) {
        this.couponMapper = couponMapper;
    }

    @Override
    public PageVO<AdminCouponVO> list(int page, int pageSize, String keyword, String status) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        String safeKeyword = normalize(keyword);
        String safeStatus = normalizeStatus(status);
        LocalDateTime now = LocalDateTime.now();
        int offset = (safePage - 1) * safePageSize;
        List<AdminCouponVO> list = couponMapper
                .listAdmin(safeKeyword, safeStatus, now, offset, safePageSize)
                .stream().map(t -> toVO(t, now, false)).toList();
        long total = couponMapper.countAdmin(safeKeyword, safeStatus, now);
        return new PageVO<>(list, total, safePage, safePageSize);
    }

    @Override
    public AdminCouponVO get(Long id) {
        CouponTemplate template = requireTemplate(id);
        return toVO(template, LocalDateTime.now(), true);
    }

    @Override
    @Transactional
    public Long create(AdminCouponDTO dto) {
        validate(dto);
        CouponTemplate template = buildTemplate(null, dto, LocalDateTime.now());
        couponMapper.insertTemplate(template);
        saveScopes(template.getId(), dto);
        return template.getId();
    }

    @Override
    @Transactional
    public void update(Long id, AdminCouponDTO dto) {
        CouponTemplate existing = requireTemplate(id);
        assertStatus(existing, CouponConstants.TEMPLATE_PENDING, "只有未开始发放的券可以编辑");
        validate(dto);
        CouponTemplate template = buildTemplate(id, dto, LocalDateTime.now());
        couponMapper.updateTemplate(template);
        couponMapper.deleteScopes(id);
        saveScopes(id, dto);
    }

    @Override
    public void stop(Long id) {
        CouponTemplate template = requireTemplate(id);
        String status = templateStatus(template, LocalDateTime.now());
        if (CouponConstants.TEMPLATE_ENDED.equals(status)
                || CouponConstants.TEMPLATE_STOPPED.equals(status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该券已结束或已停止");
        }
        couponMapper.stopNow(id, LocalDateTime.now());
    }

    @Override
    public void delete(Long id) {
        CouponTemplate template = requireTemplate(id);
        assertStatus(template, CouponConstants.TEMPLATE_PENDING, "只有未开始发放的券可以删除");
        if (couponMapper.countIssued(id) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "已有用户领取，不能删除，请改为停止发放");
        }
        couponMapper.softDelete(id, LocalDateTime.now());
    }

    @Override
    @Transactional
    public CouponGrantResultVO grant(Long id, AdminCouponGrantDTO dto) {
        CouponTemplate template = requireTemplate(id);
        LocalDateTime now = LocalDateTime.now();
        if (template.getManualStatus() != null && template.getManualStatus() == 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该券已停止发放");
        }
        if (now.isBefore(template.getReceiveStart()) || !now.isBefore(template.getReceiveEnd())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "不在领取时间内，不能发放");
        }
        if (template.getRemainQuantity() == null || template.getRemainQuantity() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "优惠券已领完");
        }

        List<Long> userIds = resolveUserIds(dto);
        // 定向发券先校验用户是否存在：把券发给不存在的 id，券就永远没人看得到，运营还以为是发成功了
        if (!Boolean.TRUE.equals(dto.getAllUsers())) {
            Set<Long> exists = new HashSet<>(couponMapper.listExistingUserIds(userIds));
            List<String> missing = userIds.stream()
                    .filter(userId -> !exists.contains(userId))
                    .map(String::valueOf)
                    .toList();
            if (!missing.isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "用户 ID 不存在：" + String.join("、", missing));
            }
        }
        int granted = 0;
        int skipped = 0;
        for (Long userId : userIds) {
            if (couponMapper.findUserCouponByUserAndTemplate(userId, id) != null) {
                skipped++;
                continue;
            }
            // 先原子扣减库存，扣不动说明领完了
            if (couponMapper.decreaseRemain(id) == 0) {
                skipped += userIds.size() - granted - skipped;
                break;
            }
            try {
                couponMapper.insertUserCoupon(buildUserCoupon(template, userId, now));
                granted++;
            } catch (DuplicateKeyException e) {
                // 并发下已被领取：回补库存并跳过
                couponMapper.increaseRemain(id);
                skipped++;
            }
        }
        CouponTemplate latest = couponMapper.findTemplateById(id);
        return CouponGrantResultVO.builder()
                .granted(granted)
                .skipped(skipped)
                .remainQuantity(latest == null ? 0 : latest.getRemainQuantity())
                .build();
    }

    @Override
    public PageVO<CouponRecordVO> records(Long id, int page, int pageSize) {
        requireTemplate(id);
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        int offset = (safePage - 1) * safePageSize;
        List<CouponRecordVO> list = couponMapper.listRecords(id, offset, safePageSize);
        list.forEach(r -> r.setStatusDesc(recordStatusDesc(r.getStatus())));
        long total = couponMapper.countRecords(id);
        return new PageVO<>(list, total, safePage, safePageSize);
    }

    // ==================== 内部方法 ====================

    private CouponTemplate requireTemplate(Long id) {
        if (id == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "券 ID 不能为空");
        }
        CouponTemplate template = couponMapper.findTemplateById(id);
        if (template == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "优惠券不存在");
        }
        return template;
    }

    private void assertStatus(CouponTemplate template, String expected, String message) {
        if (!expected.equals(templateStatus(template, LocalDateTime.now()))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private String templateStatus(CouponTemplate template, LocalDateTime now) {
        if (template.getManualStatus() != null && template.getManualStatus() == 1) {
            return CouponConstants.TEMPLATE_STOPPED;
        }
        if (now.isBefore(template.getReceiveStart())) {
            return CouponConstants.TEMPLATE_PENDING;
        }
        if (!now.isBefore(template.getReceiveEnd())
                || template.getRemainQuantity() == null || template.getRemainQuantity() <= 0) {
            return CouponConstants.TEMPLATE_ENDED;
        }
        return CouponConstants.TEMPLATE_ACTIVE;
    }

    private CouponTemplate buildTemplate(Long id, AdminCouponDTO dto, LocalDateTime now) {
        return CouponTemplate.builder()
                .id(id)
                .name(dto.getName())
                .type(dto.getType())
                .faceValue(dto.getFaceValue())
                .discountRate(dto.getDiscountRate())
                .maxDiscount(dto.getMaxDiscount())
                .thresholdAmount(dto.getThresholdAmount())
                .scopeType(dto.getScopeType())
                .targetGroup(dto.getTargetGroup())
                .minMemberLevel(dto.getMinMemberLevel() == null ? 0L : dto.getMinMemberLevel())
                .totalQuantity(dto.getTotalQuantity())
                .remainQuantity(dto.getTotalQuantity())
                .perUserLimit(1)
                .receiveStart(dto.getReceiveStart())
                .receiveEnd(dto.getReceiveEnd())
                .validType(dto.getValidType())
                .validStart(dto.getValidStart())
                .validEnd(dto.getValidEnd())
                .validDays(dto.getValidDays())
                .manualStatus(0)
                .isDeleted(0)
                .description(dto.getDescription())
                .createTime(now)
                .updateTime(id == null ? null : now)
                .build();
    }

    private void saveScopes(Long templateId, AdminCouponDTO dto) {
        List<CouponScope> scopes = new ArrayList<>();
        if (CouponConstants.SCOPE_CATEGORY.equals(dto.getScopeType())) {
            for (Long categoryId : new LinkedHashSet<>(dto.getCategoryIds())) {
                scopes.add(CouponScope.builder()
                        .couponTemplateId(templateId)
                        .targetType(CouponConstants.SCOPE_CATEGORY)
                        .targetId(categoryId)
                        .build());
            }
        } else if (CouponConstants.SCOPE_PRODUCT.equals(dto.getScopeType())) {
            for (Long productId : new LinkedHashSet<>(dto.getProductIds())) {
                scopes.add(CouponScope.builder()
                        .couponTemplateId(templateId)
                        .targetType(CouponConstants.SCOPE_PRODUCT)
                        .targetId(productId)
                        .build());
            }
        }
        if (!scopes.isEmpty()) {
            couponMapper.insertScopes(scopes);
        }
    }

    private UserCoupon buildUserCoupon(CouponTemplate template, Long userId, LocalDateTime now) {
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
                .code("CP" + now.format(CODE_FMT) + String.format("%04d", ThreadLocalRandom.current().nextInt(10000)))
                .templateId(template.getId())
                .userId(userId)
                .status(CouponConstants.USER_UNUSED)
                .receiveTime(now)
                .startTime(start)
                .endTime(end)
                .source(CouponConstants.SOURCE_ADMIN)
                .build();
    }

    private List<Long> resolveUserIds(AdminCouponGrantDTO dto) {
        if (dto == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "发放参数不能为空");
        }
        if (Boolean.TRUE.equals(dto.getAllUsers())) {
            return couponMapper.listActiveUserIds();
        }
        if (dto.getUserIds() == null || dto.getUserIds().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择要发放的用户");
        }
        return new ArrayList<>(new LinkedHashSet<>(dto.getUserIds()));
    }

    private AdminCouponVO toVO(CouponTemplate t, LocalDateTime now, boolean withScopes) {
        String status = templateStatus(t, now);
        List<Long> categoryIds = new ArrayList<>();
        List<Long> productIds = new ArrayList<>();
        if (withScopes) {
            for (CouponScope scope : couponMapper.listScopes(t.getId())) {
                if (CouponConstants.SCOPE_CATEGORY.equals(scope.getTargetType())) {
                    categoryIds.add(scope.getTargetId());
                } else if (CouponConstants.SCOPE_PRODUCT.equals(scope.getTargetType())) {
                    productIds.add(scope.getTargetId());
                }
            }
        }
        return AdminCouponVO.builder()
                .id(t.getId())
                .name(t.getName())
                .type(t.getType())
                .typeDesc(CouponConstants.TYPE_DISCOUNT.equals(t.getType()) ? "折扣券" : "满减券")
                .faceValue(t.getFaceValue())
                .discountRate(t.getDiscountRate())
                .maxDiscount(t.getMaxDiscount())
                .thresholdAmount(t.getThresholdAmount())
                .scopeType(t.getScopeType())
                .scopeTypeDesc(scopeDesc(t.getScopeType()))
                .targetGroup(t.getTargetGroup())
                .targetGroupDesc(CouponConstants.TARGET_NEW_USER.equals(t.getTargetGroup()) ? "新用户专享" : "全部用户")
                .minMemberLevel(t.getMinMemberLevel())
                .receiveStart(t.getReceiveStart())
                .receiveEnd(t.getReceiveEnd())
                .validType(t.getValidType())
                .validTypeDesc(CouponConstants.VALID_RECEIVE_DAYS.equals(t.getValidType()) ? "领取后 N 天" : "固定时段")
                .validStart(t.getValidStart())
                .validEnd(t.getValidEnd())
                .validDays(t.getValidDays())
                .totalQuantity(t.getTotalQuantity())
                .remainQuantity(t.getRemainQuantity())
                .perUserLimit(t.getPerUserLimit())
                .description(t.getDescription())
                .status(status)
                .statusDesc(statusDesc(status))
                .issuedCount(couponMapper.countIssued(t.getId()))
                .usedCount(couponMapper.countUsed(t.getId()))
                .usedDiscount(couponMapper.sumUsedDiscount(t.getId()))
                .categoryIds(withScopes ? categoryIds : null)
                .productIds(withScopes ? productIds : null)
                .createTime(t.getCreateTime())
                .build();
    }

    private void validate(AdminCouponDTO dto) {
        if (dto == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "券参数不能为空");
        }
        String name = normalize(dto.getName());
        if (name == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "券名称不能为空");
        }
        if (name.length() > 128) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "券名称不能超过 128 字");
        }
        dto.setName(name);

        String type = upper(dto.getType());
        if (!CouponConstants.TYPE_FIXED.equals(type) && !CouponConstants.TYPE_DISCOUNT.equals(type)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "券类型不合法");
        }
        dto.setType(type);

        BigDecimal threshold = dto.getThresholdAmount() == null ? BigDecimal.ZERO : dto.getThresholdAmount();
        if (threshold.signum() < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "使用门槛不能为负数");
        }
        dto.setThresholdAmount(threshold);

        if (CouponConstants.TYPE_FIXED.equals(type)) {
            if (dto.getFaceValue() == null || dto.getFaceValue().signum() <= 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "满减金额必须大于 0");
            }
            dto.setDiscountRate(null);
            dto.setMaxDiscount(null);
        } else {
            if (dto.getDiscountRate() == null || dto.getDiscountRate().signum() <= 0
                    || dto.getDiscountRate().compareTo(BigDecimal.ONE) >= 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "折扣率必须在 0 到 1 之间（如 0.85）");
            }
            if (dto.getMaxDiscount() != null && dto.getMaxDiscount().signum() < 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "最高优惠不能为负数");
            }
            dto.setFaceValue(null);
        }

        String scopeType = dto.getScopeType() == null ? CouponConstants.SCOPE_ALL : upper(dto.getScopeType());
        if (!Set.of(CouponConstants.SCOPE_ALL, CouponConstants.SCOPE_CATEGORY,
                CouponConstants.SCOPE_PRODUCT).contains(scopeType)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "适用范围不合法");
        }
        dto.setScopeType(scopeType);
        if (CouponConstants.SCOPE_CATEGORY.equals(scopeType)
                && (dto.getCategoryIds() == null || dto.getCategoryIds().isEmpty())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择适用分类");
        }
        if (CouponConstants.SCOPE_PRODUCT.equals(scopeType)
                && (dto.getProductIds() == null || dto.getProductIds().isEmpty())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择适用商品");
        }

        String target = dto.getTargetGroup() == null ? CouponConstants.TARGET_ALL : upper(dto.getTargetGroup());
        if (!CouponConstants.TARGET_ALL.equals(target) && !CouponConstants.TARGET_NEW_USER.equals(target)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "目标人群不合法");
        }
        dto.setTargetGroup(target);
        if (dto.getMinMemberLevel() != null && dto.getMinMemberLevel() < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "会员等级门槛不合法");
        }
        if (dto.getMinMemberLevel() == null) {
            dto.setMinMemberLevel(0L);
        }

        if (dto.getTotalQuantity() == null || dto.getTotalQuantity() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "发行总量必须大于 0");
        }
        if (dto.getReceiveStart() == null || dto.getReceiveEnd() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "领取时间不能为空");
        }
        if (!dto.getReceiveStart().isBefore(dto.getReceiveEnd())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "领取开始时间必须早于结束时间");
        }

        String validType = dto.getValidType() == null ? CouponConstants.VALID_FIXED : upper(dto.getValidType());
        if (!CouponConstants.VALID_FIXED.equals(validType)
                && !CouponConstants.VALID_RECEIVE_DAYS.equals(validType)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "有效期类型不合法");
        }
        dto.setValidType(validType);
        if (CouponConstants.VALID_FIXED.equals(validType)) {
            if (dto.getValidStart() == null || dto.getValidEnd() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "固定有效期不能为空");
            }
            if (!dto.getValidStart().isBefore(dto.getValidEnd())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "有效期开始必须早于结束");
            }
            dto.setValidDays(null);
        } else {
            if (dto.getValidDays() == null || dto.getValidDays() <= 0 || dto.getValidDays() > 365) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "领取后有效天数必须在 1~365 之间");
            }
            dto.setValidStart(null);
            dto.setValidEnd(null);
        }

        String description = normalize(dto.getDescription());
        if (description != null && description.length() > 255) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "使用说明不能超过 255 字");
        }
        dto.setDescription(description);
    }

    private String scopeDesc(String scope) {
        return switch (scope == null ? "" : scope) {
            case CouponConstants.SCOPE_CATEGORY -> "指定分类";
            case CouponConstants.SCOPE_PRODUCT -> "指定商品";
            default -> "全场通用";
        };
    }

    private String statusDesc(String status) {
        return switch (status) {
            case CouponConstants.TEMPLATE_PENDING -> "未开始";
            case CouponConstants.TEMPLATE_ACTIVE -> "发放中";
            case CouponConstants.TEMPLATE_STOPPED -> "已停止";
            default -> "已结束";
        };
    }

    private String recordStatusDesc(String status) {
        return switch (status) {
            case CouponConstants.USER_USED -> "已使用";
            case CouponConstants.USER_LOCKED -> "使用中";
            default -> "未使用";
        };
    }

    private String normalizeStatus(String status) {
        String value = normalize(status);
        if (value == null) {
            return null;
        }
        value = value.toUpperCase();
        if (!Set.of(CouponConstants.TEMPLATE_PENDING, CouponConstants.TEMPLATE_ACTIVE,
                CouponConstants.TEMPLATE_ENDED, CouponConstants.TEMPLATE_STOPPED).contains(value)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "券状态不合法");
        }
        return value;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String upper(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase();
    }
}
