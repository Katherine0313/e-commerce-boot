package ecommerce.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 券适用范围（coupon_scope 表）：分类或商品。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponScope {
    private Long id;
    private Long couponTemplateId;
    /** CATEGORY / PRODUCT */
    private String targetType;
    private Long targetId;
}
