package ecommerce.aftersale.enums;

/**
 * 支付方式枚举（对应 orders.pay_type 字符串，与 order-service 的 PayTypeEnum 取值一致）。
 * <p>
 * 库里存英文 code（WeChat/Alipay/BankCard），展示用中文 desc。
 */
public enum PayTypeEnum {
    WECHAT("WeChat", "微信支付"),
    ALIPAY("Alipay", "支付宝"),
    BANK_CARD("BankCard", "银行卡");

    private final String code;
    private final String desc;

    PayTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /** 按 code 反查（忽略大小写），未知返回 null */
    public static PayTypeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PayTypeEnum t : values()) {
            if (t.code.equalsIgnoreCase(code)) {
                return t;
            }
        }
        return null;
    }
}
