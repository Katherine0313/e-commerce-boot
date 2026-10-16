package ecommerce.order.enums;

/**
 * 支付方式枚举（对应 orders.pay_type 字符串）。
 * <p>
 * 库里存英文 code（WeChat/Alipay/BankCard），展示用中文 desc，前端直接用 desc 即可。
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

    /** 按 code 反查（忽略大小写，兼容历史数据 WeChat 与前端传的小写 wechat），未知返回 null */
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
