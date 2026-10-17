package ecommerce.order.enums;

/**
 * 快递公司枚举（对应 orders.courier 存英文 key）。
 * <p>
 * 库里存英文 code（shunfeng 等），展示用中文 desc。字典照旧 Node 后端 constants/dict.js 的 COURIER_MAP。
 */
public enum CourierEnum {
    SHUNFENG("shunfeng", "顺丰速运"),
    YUANTONG("yuantong", "圆通速递"),
    ZHONGTONG("zhongtong", "中通快递"),
    YUNDA("yunda", "韵达快递"),
    SHENTONG("shentong", "申通快递"),
    JITU("jitu", "极兔速递"),
    DEBANG("debang", "德邦快递"),
    YOUZHENGGUONEI("youzhengguonei", "邮政国内");

    private final String code;
    private final String desc;

    CourierEnum(String code, String desc) {
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
    public static CourierEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CourierEnum c : values()) {
            if (c.code.equalsIgnoreCase(code)) {
                return c;
            }
        }
        return null;
    }
}
