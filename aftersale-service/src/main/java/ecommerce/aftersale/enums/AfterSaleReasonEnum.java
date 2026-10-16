package ecommerce.aftersale.enums;

/**
 * 售后原因（对应 after_sales.reason 字符串）。
 */
public enum AfterSaleReasonEnum {
    QUALITY("quality", "质量问题"),
    SEVEN_DAYS("seven_days", "七天无理由"),
    WRONG_GOODS("wrong_goods", "发错货"),
    REJECTED("rejected", "用户拒收"),
    OTHER("other", "其他"),
    CANCEL("cancel", "用户取消订单");

    private final String code;
    private final String desc;

    AfterSaleReasonEnum(String code, String desc) {
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
    public static AfterSaleReasonEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AfterSaleReasonEnum r : values()) {
            if (r.code.equals(code)) {
                return r;
            }
        }
        return null;
    }
}
