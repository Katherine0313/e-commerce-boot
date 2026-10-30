package ecommerce.common.promotion;

/**
 * 促销模块常量：活动类型 / 玩法 / 人群 / 范围 / 状态。
 * <p>
 * 与数据库里的字符串值一一对应，product-service 与 order-service 共用，避免两边拼错。
 */
public final class PromotionConstants {

    /** 直减类：改商品价格 */
    public static final String TYPE_DIRECT = "DIRECT";
    /** 满减类：改商品小计 */
    public static final String TYPE_FULL_REDUCE = "FULL_REDUCE";

    /** 限时折扣（直减类） */
    public static final String RULE_DISCOUNT = "DISCOUNT";
    /** 新用户专享（直减类） */
    public static final String RULE_NEW_USER = "NEW_USER";
    /** 满额减（满减类） */
    public static final String RULE_FULL_AMOUNT_REDUCE = "FULL_AMOUNT_REDUCE";
    /** 满额折（满减类） */
    public static final String RULE_FULL_AMOUNT_DISCOUNT = "FULL_AMOUNT_DISCOUNT";

    /** 全部用户 */
    public static final String TARGET_ALL = "ALL";
    /** 未下单过的新用户 */
    public static final String TARGET_NEW_USER = "NEW_USER";

    /** 全场 */
    public static final String SCOPE_ALL = "ALL";
    /** 指定商品 */
    public static final String SCOPE_PRODUCT = "PRODUCT";

    /** 未开始 */
    public static final String STATUS_PENDING = "PENDING";
    /** 进行中 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 已结束（自然结束） */
    public static final String STATUS_ENDED = "ENDED";
    /** 已失效（运营提前结束或删除） */
    public static final String STATUS_CANCELLED = "CANCELLED";

    private PromotionConstants() {
    }
}
