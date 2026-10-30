package ecommerce.product.service;

import ecommerce.product.vo.ProductVO;

import java.util.List;

/**
 * C 端促销展示 Service。
 */
public interface PromotionService {

    /**
     * 给商品 VO 批量填充活动价与促销标签（只处理全员直减活动）。
     * <p>
     * 有活动时 price 为活动价、originalPrice 为原价、promotionTag 为展示标签。
     */
    void applyDisplay(List<ProductVO> products);
}
