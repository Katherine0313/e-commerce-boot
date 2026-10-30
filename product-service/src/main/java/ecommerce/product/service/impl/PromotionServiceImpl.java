package ecommerce.product.service.impl;

import ecommerce.common.promotion.PromotionConstants;
import ecommerce.product.entity.Promotion;
import ecommerce.product.entity.PromotionItem;
import ecommerce.product.mapper.PromotionMapper;
import ecommerce.product.service.PromotionService;
import ecommerce.product.vo.ProductVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * C 端促销展示实现：商品页免登录，只展示「全员」直减活动，新用户专享在下单时判定。
 */
@Slf4j
@Service
public class PromotionServiceImpl implements PromotionService {

    private final PromotionMapper promotionMapper;

    public PromotionServiceImpl(PromotionMapper promotionMapper) {
        this.promotionMapper = promotionMapper;
    }

    @Override
    public void applyDisplay(List<ProductVO> products) {
        if (products == null || products.isEmpty()) {
            return;
        }
        try {
            doApplyDisplay(products);
        } catch (RuntimeException e) {
            // 促销表未迁移或临时故障时不影响商品浏览，只是不展示促销信息
            log.warn("促销配置读取失败，跳过促销展示: {}", e.getMessage());
        }
    }

    private void doApplyDisplay(List<ProductVO> products) {
        // 商品页可能未登录，无法判断是否新用户，只取全员活动；新用户专享在确认页/下单时生效
        List<Promotion> active = promotionMapper.listActive(LocalDateTime.now()).stream()
                .filter(p -> PromotionConstants.TYPE_DIRECT.equals(p.getType()))
                .filter(p -> !PromotionConstants.TARGET_NEW_USER.equals(p.getTargetGroup()))
                .toList();
        if (active.isEmpty()) {
            return;
        }
        Set<Long> productIds = products.stream()
                .map(ProductVO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (productIds.isEmpty()) {
            return;
        }
        List<Long> promotionIds = active.stream().map(Promotion::getId).toList();
        Map<Long, Promotion> promoById = active.stream()
                .collect(Collectors.toMap(Promotion::getId, p -> p, (a, b) -> a));

        // 同一商品命中多个活动时取活动价最低的（与算价引擎保持一致）
        Map<Long, PromotionItem> bestByProduct = new HashMap<>();
        for (PromotionItem item : promotionMapper.listItemsByPromotionIds(promotionIds)) {
            if (item.getProductId() == null || item.getPromotionPrice() == null
                    || !productIds.contains(item.getProductId())) {
                continue;
            }
            PromotionItem current = bestByProduct.get(item.getProductId());
            if (current == null || item.getPromotionPrice().compareTo(current.getPromotionPrice()) < 0) {
                bestByProduct.put(item.getProductId(), item);
            }
        }

        for (ProductVO vo : products) {
            PromotionItem item = bestByProduct.get(vo.getId());
            if (item == null || vo.getPrice() == null
                    || item.getPromotionPrice().compareTo(vo.getPrice()) >= 0) {
                continue;
            }
            vo.setOriginalPrice(vo.getPrice());
            vo.setPrice(item.getPromotionPrice());
            vo.setPromotionTag(tagOf(promoById.get(item.getPromotionId())));
        }
    }

    private String tagOf(Promotion promotion) {
        if (promotion == null) {
            return "限时折扣";
        }
        if (promotion.getTagName() != null && !promotion.getTagName().isBlank()) {
            return promotion.getTagName();
        }
        return PromotionConstants.RULE_NEW_USER.equals(promotion.getRuleType()) ? "新人专享" : "限时折扣";
    }
}
