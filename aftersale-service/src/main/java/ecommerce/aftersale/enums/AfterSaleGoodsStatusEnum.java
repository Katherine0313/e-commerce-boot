package ecommerce.aftersale.enums;

/**
 * 货物状态（对应 after_sales.goods_status 字符串，仅已发货订单申请售后时填写）。
 */
public enum AfterSaleGoodsStatusEnum {
    NOT_RECEIVED("not_received", "未收到货"),
    RECEIVED("received", "已收到货"),
    REJECTED("rejected", "已拒收");

    private final String code;
    private final String desc;

    AfterSaleGoodsStatusEnum(String code, String desc) {
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
    public static AfterSaleGoodsStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AfterSaleGoodsStatusEnum g : values()) {
            if (g.code.equals(code)) {
                return g;
            }
        }
        return null;
    }
}
