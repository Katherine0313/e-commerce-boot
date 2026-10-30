package ecommerce.order.support;

/**
 * 优惠券常量：类型 / 范围 / 人群 / 有效期 / 模板状态 / 用户券状态。
 */
public final class CouponConstants {

    /** 满减券 */
    public static final String TYPE_FIXED = "FIXED";
    /** 折扣券 */
    public static final String TYPE_DISCOUNT = "DISCOUNT";

    public static final String SCOPE_ALL = "ALL";
    public static final String SCOPE_CATEGORY = "CATEGORY";
    public static final String SCOPE_PRODUCT = "PRODUCT";

    public static final String TARGET_ALL = "ALL";
    public static final String TARGET_NEW_USER = "NEW_USER";

    public static final String VALID_FIXED = "FIXED";
    public static final String VALID_RECEIVE_DAYS = "RECEIVE_DAYS";

    /** 模板状态（读时推导） */
    public static final String TEMPLATE_PENDING = "PENDING";
    public static final String TEMPLATE_ACTIVE = "ACTIVE";
    public static final String TEMPLATE_ENDED = "ENDED";
    public static final String TEMPLATE_STOPPED = "STOPPED";

    /** 用户券状态 */
    public static final String USER_UNUSED = "UNUSED";
    public static final String USER_LOCKED = "LOCKED";
    public static final String USER_USED = "USED";

    public static final String SOURCE_RECEIVE = "RECEIVE";
    public static final String SOURCE_ADMIN = "ADMIN";

    private CouponConstants() {
    }
}
