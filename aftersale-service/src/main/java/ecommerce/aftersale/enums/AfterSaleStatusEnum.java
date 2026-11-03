package ecommerce.aftersale.enums;

/**
 * 售后状态（对应 after_sales.status 字符串）。
 * <p>
 * 照原 Node 后端 dict.js 的 AFTER_SALE_STATUS，去掉已废弃的 returned 状态，
 * 并去掉 refund_confirming 状态 —— 项目不接真实支付，没有「等款到账」这个真实时刻，
 * 「确认退款」是一个操作（ADMIN_CONFIRM_REFUND），不是一个状态。
 */
public enum AfterSaleStatusEnum {
    REVIEWING("reviewing", "商家审核中"),
    RETURNING("returning", "退货中"),
    REFUNDING("refunding", "退款中"),
    EXCHANGING("exchanging", "换货中"),
    EXCHANGED("exchanged", "已换货"),
    REFUNDED("refunded", "已退款"),
    REJECTED("rejected", "售后拒绝");

    private final String code;
    private final String desc;

    AfterSaleStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /** 按 code 反查，未知返回 null */
    public static AfterSaleStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AfterSaleStatusEnum s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        return null;
    }
}
