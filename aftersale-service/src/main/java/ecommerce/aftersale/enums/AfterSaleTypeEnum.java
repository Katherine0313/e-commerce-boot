package ecommerce.aftersale.enums;

/**
 * 售后类型（对应 after_sales.after_sale_type 字符串）。
 */
public enum AfterSaleTypeEnum {
    ONLY_REFUND("only_refund", "仅退款"),
    RETURN_REFUND("return_refund", "退货退款"),
    EXCHANGE("exchange", "换货");

    private final String code;
    private final String desc;

    AfterSaleTypeEnum(String code, String desc) {
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
    public static AfterSaleTypeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AfterSaleTypeEnum t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        return null;
    }
}
