package ecommerce.aftersale.enums;

import lombok.Getter;

/**
 * 售后操作类型枚举 AfterSaleOperateTypeEnum。
 * <p>
 * 一次操作 = 一个 operate_type + 一个 after_status，二者一一对应。
 * <p>
 * 目标状态不在本枚举里定义 —— 状态跃迁（从哪个状态能到哪个状态）的唯一真相是
 * {@code support.AfterSaleTransitions}，枚举只负责「这次操作叫什么、给买家看什么文案」。
 */
@Getter
public enum AfterSaleOperateTypeEnum {

    /** 1 用户提交售后申请（目标状态 reviewing） */
    USER_SUBMIT(1, "USER_SUBMIT", "用户提交售后申请", "用户提交售后申请"),

    /** 2 商家审核-拒绝售后（目标状态 rejected） */
    ADMIN_AUDIT_REJECT(2, "ADMIN_AUDIT_REJECT", "商家审核-拒绝售后", "商家已拒绝售后申请"),

    /** 3 商家审核-同意仅退款（目标状态 refunding） */
    ADMIN_AUDIT_AGREE_REFUND(3, "ADMIN_AUDIT_AGREE_REFUND", "商家审核-同意仅退款",
            "商家已同意【仅退款】售后申请，退款处理中"),

    /** 4 商家审核-同意退货退款（目标状态 returning） */
    ADMIN_AUDIT_AGREE_RETURN(4, "ADMIN_AUDIT_AGREE_RETURN", "商家审核-同意退货退款",
            "商家已同意【退货退款】售后申请，请买家填写退货物流"),

    /** 5 商家审核-同意换货（目标状态 returning） */
    ADMIN_AUDIT_AGREE_EXCHANGE(5, "ADMIN_AUDIT_AGREE_EXCHANGE", "商家审核-同意换货",
            "商家已同意【换货】售后申请，请买家填写退货物流"),

    /** 6 买家填写退货物流（目标状态：退货退款 → refunding；换货 → returning 不变） */
    USER_FILL_LOGISTICS(6, "USER_FILL_LOGISTICS", "买家填写退货物流", "买家已填写退货物流"),

    /** 7 商家确认退款完成（目标状态 refunded） */
    ADMIN_CONFIRM_REFUND(7, "ADMIN_CONFIRM_REFUND", "商家确认退款完成", "商家已确认退款，退款处理完成"),

    /** 8 商家确认收到退货，开始换货（目标状态 exchanging） */
    ADMIN_START_EXCHANGE(8, "ADMIN_START_EXCHANGE", "商家开始换货", "商家已确认收到退货，开始换货"),

    /** 9 换货完成（目标状态 exchanged） */
    ADMIN_FINISH_EXCHANGE(9, "ADMIN_FINISH_EXCHANGE", "换货完成", "换货已完成");

    /** 数据库存储编码 */
    private final Integer code;

    /** 英文标识（代码里判断用） */
    private final String tag;

    /** 操作名称（后台下拉、筛选用） */
    private final String desc;

    /** 审计日志文案（买家可见，写入 after_sale_log.content） */
    private final String logContent;

    AfterSaleOperateTypeEnum(Integer code, String tag, String desc, String logContent) {
        this.code = code;
        this.tag = tag;
        this.desc = desc;
        this.logContent = logContent;
    }

    /**
     * 根据 code 反查枚举，未知返回 null。
     */
    public static AfterSaleOperateTypeEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (AfterSaleOperateTypeEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
