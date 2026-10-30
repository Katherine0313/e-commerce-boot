package ecommerce.order.service;

import ecommerce.common.promotion.QuoteItemInput;
import ecommerce.common.promotion.QuoteResult;

import java.util.List;

/**
 * 订单侧促销算价 Service。
 */
public interface PromotionPricingService {

    /** 完整报价：直减 + 满减（确认页 / 下单用） */
    QuoteResult quote(List<QuoteItemInput> items, Long userId);

    /** 只算直减：购物车展示用（满减要按整单金额算，不能按购物车部分商品算） */
    QuoteResult quoteDirect(List<QuoteItemInput> items, Long userId);

    /** 下单成功后写促销使用记录（后台效果统计用） */
    void recordUsage(QuoteResult quote, Long orderId, String orderNo, Long userId);
}
