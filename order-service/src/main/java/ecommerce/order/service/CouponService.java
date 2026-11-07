package ecommerce.order.service;

import ecommerce.order.dto.OrderItemDTO;
import ecommerce.order.vo.AvailableCouponVO;
import ecommerce.order.vo.CouponCenterVO;
import ecommerce.order.vo.ProductSubsidyVO;
import ecommerce.order.vo.UserCouponVO;

import java.util.List;

/**
 * C 端优惠券 Service。
 */
public interface CouponService {

    /** 领券中心 */
    List<CouponCenterVO> center();

    /** 商品详情页「国家补贴」横幅；没有可领模板返回 null */
    ProductSubsidyVO productSubsidy(Long productId);

    /** 领券 */
    UserCouponVO claim(Long templateId);

    /** 我的优惠券；status：AVAILABLE / USED / EXPIRED，null 查全部 */
    List<UserCouponVO> myCoupons(String status);

    /** 当前订单可用券（按商品实时算可减金额） */
    List<AvailableCouponVO> available(List<OrderItemDTO> items);
}
