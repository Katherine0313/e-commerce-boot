package ecommerce.product.service.impl;

import ecommerce.common.PageVO;
import ecommerce.common.promotion.PromotionConstants;
import ecommerce.exception.BusinessException;
import ecommerce.product.dto.AdminPromotionDTO;
import ecommerce.product.dto.AdminPromotionItemDTO;
import ecommerce.product.dto.AdminPromotionRuleDTO;
import ecommerce.product.entity.Promotion;
import ecommerce.product.entity.PromotionItem;
import ecommerce.product.entity.PromotionRule;
import ecommerce.product.mapper.PromotionMapper;
import ecommerce.product.service.AdminPromotionService;
import ecommerce.product.vo.AdminPromotionVO;
import ecommerce.product.vo.PromotionItemVO;
import ecommerce.product.vo.PromotionRuleVO;
import ecommerce.product.vo.PromotionStatsVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台促销活动管理实现。
 * <p>
 * 状态规则：未开始可编辑/删除；进行中只能提前结束；已结束/已失效不可再操作。
 */
@Service
public class AdminPromotionServiceImpl implements AdminPromotionService {

    private final PromotionMapper promotionMapper;

    public AdminPromotionServiceImpl(PromotionMapper promotionMapper) {
        this.promotionMapper = promotionMapper;
    }

    @Override
    public PageVO<AdminPromotionVO> list(int page, int pageSize, String keyword, String status) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        String safeKeyword = normalize(keyword);
        String safeStatus = normalizeStatus(status);
        LocalDateTime now = LocalDateTime.now();
        int offset = (safePage - 1) * safePageSize;

        List<AdminPromotionVO> list = promotionMapper
                .listAdmin(safeKeyword, safeStatus, now, offset, safePageSize)
                .stream().map(p -> toVO(p, now)).toList();
        long total = promotionMapper.countAdmin(safeKeyword, safeStatus, now);
        return new PageVO<>(list, total, safePage, safePageSize);
    }

    @Override
    public AdminPromotionVO get(Long id) {
        Promotion promotion = requirePromotion(id);
        LocalDateTime now = LocalDateTime.now();
        AdminPromotionVO vo = toVO(promotion, now);
        vo.setItems(promotionMapper.listItemsWithProduct(id));
        vo.setRules(promotionMapper.listRules(id).stream().map(this::toRuleVO).toList());
        vo.setStats(promotionMapper.stats(id));
        return vo;
    }

    @Override
    @Transactional
    public Long create(AdminPromotionDTO dto) {
        validate(dto, null);
        Promotion promotion = Promotion.builder()
                .name(dto.getName())
                .type(dto.getType())
                .ruleType(dto.getRuleType())
                .targetGroup(dto.getTargetGroup())
                .scopeType(dto.getScopeType())
                .tagName(dto.getTagName())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .manualStatus(0)
                .isDeleted(0)
                .createTime(LocalDateTime.now())
                .build();
        promotionMapper.insert(promotion);
        saveRelations(promotion.getId(), dto);
        return promotion.getId();
    }

    @Override
    @Transactional
    public void update(Long id, AdminPromotionDTO dto) {
        Promotion existing = requirePromotion(id);
        assertEditable(existing);
        validate(dto, id);
        Promotion promotion = Promotion.builder()
                .id(id)
                .name(dto.getName())
                .type(dto.getType())
                .ruleType(dto.getRuleType())
                .targetGroup(dto.getTargetGroup())
                .scopeType(dto.getScopeType())
                .tagName(dto.getTagName())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .updateTime(LocalDateTime.now())
                .build();
        promotionMapper.update(promotion);
        promotionMapper.deleteItems(id);
        promotionMapper.deleteRules(id);
        saveRelations(id, dto);
    }

    @Override
    public void end(Long id) {
        Promotion promotion = requirePromotion(id);
        assertStatus(promotion, PromotionConstants.STATUS_ACTIVE, "只有进行中的活动可以提前结束");
        promotionMapper.endNow(id, LocalDateTime.now());
    }

    @Override
    public void delete(Long id) {
        Promotion promotion = requirePromotion(id);
        assertStatus(promotion, PromotionConstants.STATUS_PENDING, "只有未开始的活动可以删除");
        promotionMapper.softDelete(id, LocalDateTime.now());
    }

    @Override
    public PromotionStatsVO stats(Long id) {
        requirePromotion(id);
        return promotionMapper.stats(id);
    }

    // ==================== 内部方法 ====================

    private Promotion requirePromotion(Long id) {
        if (id == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "活动 ID 不能为空");
        }
        Promotion promotion = promotionMapper.findById(id);
        if (promotion == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "活动不存在");
        }
        return promotion;
    }

    private void saveRelations(Long promotionId, AdminPromotionDTO dto) {
        List<PromotionItem> items = new ArrayList<>();
        if (dto.getItems() != null) {
            for (AdminPromotionItemDTO itemDTO : dto.getItems()) {
                items.add(PromotionItem.builder()
                        .promotionId(promotionId)
                        .productId(itemDTO.getProductId())
                        .promotionPrice(itemDTO.getPromotionPrice())
                        .build());
            }
        }
        if (!items.isEmpty()) {
            promotionMapper.insertItems(items);
        }
        if (dto.getRules() != null && !dto.getRules().isEmpty()) {
            List<PromotionRule> rules = new ArrayList<>();
            int sort = 0;
            for (AdminPromotionRuleDTO ruleDTO : dto.getRules()) {
                rules.add(PromotionRule.builder()
                        .promotionId(promotionId)
                        .thresholdAmount(ruleDTO.getThresholdAmount())
                        .discountAmount(ruleDTO.getDiscountAmount())
                        .discountRate(ruleDTO.getDiscountRate())
                        .sortOrder(sort++)
                        .build());
            }
            promotionMapper.insertRules(rules);
        }
    }

    /** 校验并归一化请求参数（type/ruleType 等统一大写） */
    private void validate(AdminPromotionDTO dto, Long excludeId) {
        if (dto == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "活动参数不能为空");
        }
        String name = normalize(dto.getName());
        if (name == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "活动名称不能为空");
        }
        if (name.length() > 128) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "活动名称不能超过 128 字");
        }
        String tagName = normalize(dto.getTagName());
        if (tagName != null && tagName.length() > 32) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "促销标签不能超过 32 字");
        }
        dto.setName(name);
        dto.setTagName(tagName);

        String type = upper(dto.getType());
        if (!PromotionConstants.TYPE_DIRECT.equals(type) && !PromotionConstants.TYPE_FULL_REDUCE.equals(type)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "促销类型不合法");
        }
        dto.setType(type);

        String ruleType = upper(dto.getRuleType());
        if (PromotionConstants.TYPE_DIRECT.equals(type)) {
            if (!PromotionConstants.RULE_DISCOUNT.equals(ruleType)
                    && !PromotionConstants.RULE_NEW_USER.equals(ruleType)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "直减类玩法只支持限时折扣 / 新用户专享");
            }
            dto.setScopeType(PromotionConstants.SCOPE_PRODUCT);
        } else {
            if (!PromotionConstants.RULE_FULL_AMOUNT_REDUCE.equals(ruleType)
                    && !PromotionConstants.RULE_FULL_AMOUNT_DISCOUNT.equals(ruleType)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "满减类玩法只支持满额减 / 满额折");
            }
            String scope = dto.getScopeType() == null ? PromotionConstants.SCOPE_ALL : upper(dto.getScopeType());
            if (!PromotionConstants.SCOPE_ALL.equals(scope) && !PromotionConstants.SCOPE_PRODUCT.equals(scope)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "商品范围不合法");
            }
            dto.setScopeType(scope);
        }
        dto.setRuleType(ruleType);

        String target = dto.getTargetGroup() == null ? PromotionConstants.TARGET_ALL : upper(dto.getTargetGroup());
        if (!PromotionConstants.TARGET_ALL.equals(target) && !PromotionConstants.TARGET_NEW_USER.equals(target)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "目标人群不合法");
        }
        // 新用户专享玩法强制只对新用户生效
        if (PromotionConstants.RULE_NEW_USER.equals(ruleType)) {
            target = PromotionConstants.TARGET_NEW_USER;
        }
        dto.setTargetGroup(target);

        if (dto.getStartTime() == null || dto.getEndTime() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "活动开始/结束时间不能为空");
        }
        if (!dto.getStartTime().isBefore(dto.getEndTime())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "开始时间必须早于结束时间");
        }

        validateItems(dto, type);
        validateRules(dto, type, ruleType);

        // 直减类：同商品不能同时参加时间重叠的其他直减活动
        if (PromotionConstants.TYPE_DIRECT.equals(type)) {
            List<Long> productIds = dto.getItems().stream().map(AdminPromotionItemDTO::getProductId).toList();
            long overlap = promotionMapper.countOverlappingDirect(
                    productIds, dto.getStartTime(), dto.getEndTime(), excludeId);
            if (overlap > 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "有商品已参加时间重叠的其他直减活动，请先解绑或调整时间");
            }
        }
    }

    private void validateItems(AdminPromotionDTO dto, String type) {
        List<AdminPromotionItemDTO> items = dto.getItems() == null ? List.of() : dto.getItems();
        boolean needItems = PromotionConstants.TYPE_DIRECT.equals(type)
                || PromotionConstants.SCOPE_PRODUCT.equals(dto.getScopeType());
        if (needItems && items.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择参加活动的商品");
        }
        Set<Long> productIds = new HashSet<>();
        for (AdminPromotionItemDTO item : items) {
            if (item == null || item.getProductId() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "活动商品不能为空");
            }
            if (!productIds.add(item.getProductId())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "活动商品不能重复");
            }
        }
        if (items.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> priceMap = loadProductPrices(productIds);
        for (AdminPromotionItemDTO item : items) {
            BigDecimal salesPrice = priceMap.get(item.getProductId());
            if (salesPrice == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "商品 " + item.getProductId() + " 不存在");
            }
            if (PromotionConstants.TYPE_DIRECT.equals(type)) {
                BigDecimal promoPrice = item.getPromotionPrice();
                if (promoPrice == null || promoPrice.signum() < 0) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "活动价不能为空且不能为负数");
                }
                if (promoPrice.compareTo(salesPrice) >= 0) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST,
                            "活动价必须低于商品「" + item.getProductId() + "」的当前售价 " + salesPrice);
                }
            }
        }
    }

    private void validateRules(AdminPromotionDTO dto, String type, String ruleType) {
        if (!PromotionConstants.TYPE_FULL_REDUCE.equals(type)) {
            dto.setRules(null);
            return;
        }
        List<AdminPromotionRuleDTO> rules = dto.getRules();
        if (rules == null || rules.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "满减活动至少需要配置一档阶梯");
        }
        for (AdminPromotionRuleDTO rule : rules) {
            if (rule == null || rule.getThresholdAmount() == null
                    || rule.getThresholdAmount().signum() <= 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "满额门槛必须大于 0");
            }
            if (PromotionConstants.RULE_FULL_AMOUNT_REDUCE.equals(ruleType)) {
                if (rule.getDiscountAmount() == null || rule.getDiscountAmount().signum() <= 0) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "满减金额必须大于 0");
                }
                if (rule.getDiscountAmount().compareTo(rule.getThresholdAmount()) >= 0) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "满减金额必须小于满额门槛");
                }
                rule.setDiscountRate(null);
            } else {
                if (rule.getDiscountRate() == null
                        || rule.getDiscountRate().signum() <= 0
                        || rule.getDiscountRate().compareTo(BigDecimal.ONE) >= 0) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "折扣率必须在 0 到 1 之间（如 0.85）");
                }
                rule.setDiscountAmount(null);
            }
        }
    }

    private Map<Long, BigDecimal> loadProductPrices(Set<Long> productIds) {
        Map<Long, BigDecimal> priceMap = new HashMap<>();
        for (Map<String, Object> row : promotionMapper.selectProductPrices(new ArrayList<>(productIds))) {
            Object id = row.get("id");
            Object price = row.get("sales_price");
            if (id instanceof Number number) {
                priceMap.put(number.longValue(),
                        price == null ? null : new BigDecimal(price.toString()));
            }
        }
        return priceMap;
    }

    private void assertStatus(Promotion promotion, String expected, String message) {
        if (!expected.equals(statusOf(promotion, LocalDateTime.now()))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, message);
        }
    }

    /**
     * 未开始、进行中的活动都允许编辑。
     * 进行中的活动改价只影响改完之后的新订单：下单时价格已快照进 order_items.price，
     * 优惠金额也已写进 promotion_usage，历史订单不受影响。
     * 已结束/已失效的活动属于历史，改了没有意义，直接拦掉。
     */
    private void assertEditable(Promotion promotion) {
        String status = statusOf(promotion, LocalDateTime.now());
        if (!PromotionConstants.STATUS_PENDING.equals(status)
                && !PromotionConstants.STATUS_ACTIVE.equals(status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "只有未开始或进行中的活动可以编辑");
        }
    }

    private String statusOf(Promotion promotion, LocalDateTime now) {
        if (promotion.getManualStatus() != null && promotion.getManualStatus() == 1) {
            return PromotionConstants.STATUS_CANCELLED;
        }
        if (now.isBefore(promotion.getStartTime())) {
            return PromotionConstants.STATUS_PENDING;
        }
        if (!now.isBefore(promotion.getEndTime())) {
            return PromotionConstants.STATUS_ENDED;
        }
        return PromotionConstants.STATUS_ACTIVE;
    }

    private AdminPromotionVO toVO(Promotion p, LocalDateTime now) {
        int productCount = promotionMapper.listItemsWithProduct(p.getId()).size();
        String status = statusOf(p, now);
        return AdminPromotionVO.builder()
                .id(p.getId())
                .name(p.getName())
                .type(p.getType())
                .typeDesc(typeDesc(p.getType()))
                .ruleType(p.getRuleType())
                .ruleTypeDesc(ruleTypeDesc(p.getRuleType()))
                .targetGroup(p.getTargetGroup())
                .targetGroupDesc(targetDesc(p.getTargetGroup()))
                .scopeType(p.getScopeType())
                .scopeTypeDesc(scopeDesc(p.getScopeType()))
                .tagName(p.getTagName())
                .startTime(p.getStartTime())
                .endTime(p.getEndTime())
                .status(status)
                .statusDesc(statusDesc(status))
                .productCount(productCount)
                .createTime(p.getCreateTime())
                .build();
    }

    private PromotionRuleVO toRuleVO(PromotionRule rule) {
        return PromotionRuleVO.builder()
                .thresholdAmount(rule.getThresholdAmount())
                .discountAmount(rule.getDiscountAmount())
                .discountRate(rule.getDiscountRate())
                .build();
    }

    private String typeDesc(String type) {
        return PromotionConstants.TYPE_DIRECT.equals(type) ? "直减" : "满减";
    }

    private String ruleTypeDesc(String ruleType) {
        return switch (ruleType == null ? "" : ruleType) {
            case PromotionConstants.RULE_DISCOUNT -> "限时折扣";
            case PromotionConstants.RULE_NEW_USER -> "新用户专享";
            case PromotionConstants.RULE_FULL_AMOUNT_REDUCE -> "满额减";
            case PromotionConstants.RULE_FULL_AMOUNT_DISCOUNT -> "满额折";
            default -> ruleType;
        };
    }

    private String targetDesc(String target) {
        return PromotionConstants.TARGET_NEW_USER.equals(target) ? "未下单新用户" : "全部用户";
    }

    private String scopeDesc(String scope) {
        return PromotionConstants.SCOPE_ALL.equals(scope) ? "全场" : "指定商品";
    }

    private String statusDesc(String status) {
        return switch (status) {
            case PromotionConstants.STATUS_PENDING -> "未开始";
            case PromotionConstants.STATUS_ACTIVE -> "进行中";
            case PromotionConstants.STATUS_ENDED -> "已结束";
            default -> "已失效";
        };
    }

    private String normalizeStatus(String status) {
        String value = normalize(status);
        if (value == null) {
            return null;
        }
        value = value.toUpperCase();
        Set<String> allowed = Set.of(PromotionConstants.STATUS_PENDING, PromotionConstants.STATUS_ACTIVE,
                PromotionConstants.STATUS_ENDED, PromotionConstants.STATUS_CANCELLED);
        if (!allowed.contains(value)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "活动状态不合法");
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
