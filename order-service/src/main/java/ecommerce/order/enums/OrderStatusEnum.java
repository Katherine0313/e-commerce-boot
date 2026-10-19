package ecommerce.order.enums;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 订单状态枚举（数字 code，对应 orders.order_status TINYINT）。
 * <p>
 * desc 是 C 端（顾客）展示文案，adminDesc 是后台（商家）展示文案——同一状态两端文案可能不同，
 * 例如「待确认」（商家确认收款）对顾客展示为「待发货」，后台才叫「待确认」。
 * <p>
 * 状态机流转规则照原 Node 后端 orderStatus.js：
 * pending_pay → pending_confirm → pending_ship → shipped → finished，
 * pending_pay / pending_confirm / pending_ship 都可取消（cancel）。
 */
public enum OrderStatusEnum {
    PENDING_PAY(1, "待付款", "待付款", "gold"),
    // 待确认（商家确认收款）对 C 端展示为「待发货」：确认收款是商家后台操作，
    // 若照旧显示「待确认」，用户会误以为要自己去确认
    PENDING_CONFIRM(2, "待发货", "待确认", "blue"),
    // 待发货：C 端与后台统一叫「待发货」（原 C 端「处理中」与后台「待发货」两端文案不一致，统一掉）
    PENDING_SHIP(3, "待发货", "待发货", "cyan"),
    SHIPPED(4, "已发货", "已发货", "purple"),
    FINISHED(5, "已完成", "已完成", "success"),
    CANCEL(6, "已取消", "已取消", "default");

    private final Integer code;
    /** C 端（顾客）展示文案 */
    private final String desc;
    /** 后台（商家）展示文案 */
    private final String adminDesc;
    /** 状态标签颜色（ant-design-vue a-tag 的 color 值，照旧 Node dict.js 的 ORDER_STATUS.COLOR） */
    private final String color;

    OrderStatusEnum(Integer code, String desc, String adminDesc, String color) {
        this.code = code;
        this.desc = desc;
        this.adminDesc = adminDesc;
        this.color = color;
    }

    public Integer getCode() {
        return code;
    }

    /** C 端展示文案 */
    public String getDesc() {
        return desc;
    }

    /** 后台（商家）展示文案 */
    public String getAdminDesc() {
        return adminDesc;
    }

    /** 状态标签颜色 */
    public String getColor() {
        return color;
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

    /** 后台状态文案映射（code → adminDesc），状态常量接口用 */
    public static Map<Integer, String> adminDescMap() {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (OrderStatusEnum status : values()) {
            map.put(status.code, status.adminDesc);
        }
        return map;
    }

    /** 状态标签颜色映射（code → color），状态常量接口用 */
    public static Map<Integer, String> colorMap() {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (OrderStatusEnum status : values()) {
            map.put(status.code, status.color);
        }
        return map;
    }
}
