package ecommerce.order.enums;

/**
 * 发货流水操作类型（对应 order_delivery_log.operate_type）。
 * <p>
 * 本期只有后台人工操作三种：发货、补发、改快递信息。
 */
public enum DeliveryOperateTypeEnum {
    CREATE(1, "创建发货"),
    RESEND(2, "补发"),
    UPDATE(3, "修改快递信息");

    private final Integer code;
    private final String desc;

    DeliveryOperateTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /** 按数字 code 反查枚举，未知 code 返回 null */
    public static DeliveryOperateTypeEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (DeliveryOperateTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
