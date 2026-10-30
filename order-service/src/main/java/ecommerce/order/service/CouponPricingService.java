package ecommerce.order.service;

import ecommerce.common.promotion.QuoteItemInput;
import ecommerce.common.promotion.QuoteResult;
import ecommerce.order.vo.AvailableCouponVO;

import java.util.List;

/**
 * 优惠券算价 Service：在促销报价之上叠加券优惠。
 */
public interface CouponPricingService {

    /**
     * 对促销报价叠加指定用户券；券不可用会抛 BusinessException。
     *
     * @param userCouponId 用户券 id；null 表示不用券
     */
    QuoteResult applyCoupon(QuoteResult promotionQuote, List<QuoteItemInput> inputs,
                            Long userCouponId, Long userId);

    /**
     * 当前订单可用券列表：每张券算好可减金额与不可用原因。
     */
    List<AvailableCouponVO> listAvailable(List<QuoteItemInput> inputs, Long userId);
}
