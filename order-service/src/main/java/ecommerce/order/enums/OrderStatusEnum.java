package ecommerce.order.enums;

import java.util.EnumSet;
import java.util.Map;

/**
 * 订单状态枚举（数字 code，对应 orders.order_status TINYINT）。
 * <p>
 * 状态机流转规则照原 Node 后端 orderStatus.js：
 * pending_pay → pending_confirm → pending_ship → shipped → finished，
 * pending_pay / pending_confirm / pending_ship 都可取消（cancel）。
 */
public enum OrderStatusEnum {
    PENDING_PAY(1, "待付款"),
    PENDING_CONFIRM(2, "待确认"),
    PENDING_SHIP(3, "待发货"),
    SHIPPED(4, "已发货"),
    FINISHED(5, "已完成"),
    CANCEL(6, "已取消");

    private final Integer code;
    private final String desc;

    OrderStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /** 每个状态允许流转到的下一个状态集合（终态为 empty） */
    private static final Map<OrderStatusEnum, EnumSet<OrderStatusEnum>> TRANSITIONS = Map.of(
            PENDING_PAY, EnumSet.of(PENDING_CONFIRM, CANCEL),
            PENDING_CONFIRM, EnumSet.of(PENDING_SHIP, CANCEL),
            PENDING_SHIP, EnumSet.of(SHIPPED, CANCEL),
            SHIPPED, EnumSet.of(FINISHED),
            FINISHED, EnumSet.noneOf(OrderStatusEnum.class),
            CANCEL, EnumSet.noneOf(OrderStatusEnum.class)
    );

    /** 是否允许从当前状态流转到目标状态 */
    public boolean canTransitionTo(OrderStatusEnum target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /** 按数字 code 反查枚举，未知 code 返回 null */
    public static OrderStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
