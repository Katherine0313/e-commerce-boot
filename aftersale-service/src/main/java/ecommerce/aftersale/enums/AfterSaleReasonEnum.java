package ecommerce.aftersale.enums;

/**
 * 售后原因（对应 after_sales.reason 字符串）。
 * <p>
 * 分两套：未发货原因（shipped=false）与已发货原因（shipped=true），
 * 前端按订单状态（未发货/已发货）展示对应一套。
 */
public enum AfterSaleReasonEnum {
    // ============ 未发货原因 ============
    DUPLICATE("duplicate", "多拍/错拍/不想要", false),
    CANCEL("cancel", "不想买了，申请取消订单", false),
    LATE_DELIVERY("late_delivery", "商家未按约定时间发货", false),
    WRONG_SPEC("wrong_spec", "选错规格/型号", false),
    OTHER_UNSHIPPED("other_unshipped", "其他", false),

    // ============ 已发货原因 ============
    SEVEN_DAYS("seven_days", "七天无理由退货", true),
    QUALITY("quality", "商品质量问题", true),
    WRONG_GOODS("wrong_goods", "发错货/规格不对", true),
    NOT_AS_DESCRIBED("not_as_described", "商品与描述不符", true),
    DAMAGED("damaged", "包裹破损、商品损坏", true),
    MISSING_ITEM("missing_item", "漏发/少发商品", true),
    LOST_PARCEL("lost_parcel", "物流长时间未更新/丢件", true),
    DONT_WANT("dont_want", "不想要了", true),
    OTHER_SHIPPED("other_shipped", "其他", true);

    private final String code;
    private final String desc;
    /** 是否已发货原因（true=已发货，false=未发货） */
    private final boolean shipped;

    AfterSaleReasonEnum(String code, String desc, boolean shipped) {
        this.code = code;
        this.desc = desc;
        this.shipped = shipped;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public boolean isShipped() {
        return shipped;
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
