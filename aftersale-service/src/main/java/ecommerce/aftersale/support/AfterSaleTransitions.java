package ecommerce.aftersale.support;

import ecommerce.aftersale.enums.AfterSaleOperateTypeEnum;
import ecommerce.aftersale.enums.AfterSaleStatusEnum;
import ecommerce.aftersale.enums.AfterSaleTypeEnum;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 售后状态机 —— 状态跃迁的唯一真相。
 * <p>
 * 两个用途：
 * <ol>
 *   <li><b>校验</b>：后台审核时校验「当前状态 → 目标状态」在不在白名单里，不在就拒绝；</li>
 *   <li><b>下发</b>：后台详情接口把当前状态可执行的动作（availableActions）下发给前端，
 *       前端纯渲染，不再自己维护一份状态表（两份拷贝必然漂移）。</li>
 * </ol>
 * 规则按 (售后类型, 当前状态) 索引，每个动作带目标状态、按钮文案、危险标记和操作类型。
 */
public final class AfterSaleTransitions {

    /** 按钮类型 */
    public enum Kind {
        /** 正向操作（同意退款、确认退款…） */
        PRIMARY,
        /** 危险操作（拒绝售后） */
        DANGER
    }

    /**
     * 一个可执行动作。
     *
     * @param status                 目标状态 code
     * @param label                  后台按钮文案
     * @param kind                   按钮类型
     * @param operateType            操作类型（写 after_sale_log.operate_type）
     * @param requiresReturnSubmitted 是否要求买家已填退货物流（换货的「确认收到退货」要求）
     */
    public record Action(String status, String label, Kind kind,
                         AfterSaleOperateTypeEnum operateType, boolean requiresReturnSubmitted) {
    }

    private static final String ONLY_REFUND = AfterSaleTypeEnum.ONLY_REFUND.getCode();
    private static final String RETURN_REFUND = AfterSaleTypeEnum.RETURN_REFUND.getCode();
    private static final String EXCHANGE = AfterSaleTypeEnum.EXCHANGE.getCode();

    private static final String REVIEWING = AfterSaleStatusEnum.REVIEWING.getCode();
    private static final String RETURNING = AfterSaleStatusEnum.RETURNING.getCode();
    private static final String REFUNDING = AfterSaleStatusEnum.REFUNDING.getCode();
    private static final String REFUNDED = AfterSaleStatusEnum.REFUNDED.getCode();
    private static final String REJECTED = AfterSaleStatusEnum.REJECTED.getCode();
    private static final String EXCHANGING = AfterSaleStatusEnum.EXCHANGING.getCode();
    private static final String EXCHANGED = AfterSaleStatusEnum.EXCHANGED.getCode();

    /** (售后类型|当前状态) → 可执行动作 */
    private static final Map<String, List<Action>> RULES = buildRules();

    private AfterSaleTransitions() {
    }

    private static Map<String, List<Action>> buildRules() {
        Map<String, List<Action>> rules = new LinkedHashMap<>();

        // 仅退款：审核通过 → 退款中 → 确认退款 → 已退款
        rules.put(key(ONLY_REFUND, REVIEWING), List.of(
                action(REFUNDING, "同意退款", Kind.PRIMARY, AfterSaleOperateTypeEnum.ADMIN_AUDIT_AGREE_REFUND),
                action(REJECTED, "拒绝售后", Kind.DANGER, AfterSaleOperateTypeEnum.ADMIN_AUDIT_REJECT)));
        rules.put(key(ONLY_REFUND, REFUNDING), List.of(
                action(REFUNDED, "确认退款", Kind.PRIMARY, AfterSaleOperateTypeEnum.ADMIN_CONFIRM_REFUND)));

        // 退货退款：审核通过 → 退货中（买家填物流后系统自动到退款中）→ 确认退款 → 已退款
        rules.put(key(RETURN_REFUND, REVIEWING), List.of(
                action(RETURNING, "同意退货退款", Kind.PRIMARY, AfterSaleOperateTypeEnum.ADMIN_AUDIT_AGREE_RETURN),
                action(REJECTED, "拒绝售后", Kind.DANGER, AfterSaleOperateTypeEnum.ADMIN_AUDIT_REJECT)));
        rules.put(key(RETURN_REFUND, REFUNDING), List.of(
                action(REFUNDED, "确认收到退货并退款", Kind.PRIMARY, AfterSaleOperateTypeEnum.ADMIN_CONFIRM_REFUND)));

        // 换货：审核通过 → 退货中（买家填物流）→ 确认收到退货 → 换货中 → 换货完成
        rules.put(key(EXCHANGE, REVIEWING), List.of(
                action(RETURNING, "同意换货", Kind.PRIMARY, AfterSaleOperateTypeEnum.ADMIN_AUDIT_AGREE_EXCHANGE),
                action(REJECTED, "拒绝售后", Kind.DANGER, AfterSaleOperateTypeEnum.ADMIN_AUDIT_REJECT)));
        rules.put(key(EXCHANGE, RETURNING), List.of(
                action(EXCHANGING, "确认收到退货", Kind.PRIMARY,
                        AfterSaleOperateTypeEnum.ADMIN_START_EXCHANGE, true)));
        rules.put(key(EXCHANGE, EXCHANGING), List.of(
                action(EXCHANGED, "换货完成", Kind.PRIMARY, AfterSaleOperateTypeEnum.ADMIN_FINISH_EXCHANGE)));

        return Map.copyOf(rules);
    }

    /**
     * 取某售后单在当前状态下可执行的动作（后台按钮）。
     *
     * @param type           售后类型 code
     * @param fromStatus     当前状态 code
     * @param returnSubmitted 买家是否已填写退货物流（return_at 是否有值）
     */
    public static List<Action> actionsOf(String type, String fromStatus, boolean returnSubmitted) {
        return RULES.getOrDefault(key(type, fromStatus), List.of()).stream()
                .filter(action -> !action.requiresReturnSubmitted() || returnSubmitted)
                .toList();
    }

    /**
     * 查「当前状态 → 目标状态」是否允许，不允许返回 null。
     */
    public static Action find(String type, String fromStatus, String toStatus, boolean returnSubmitted) {
        if (toStatus == null) {
            return null;
        }
        return actionsOf(type, fromStatus, returnSubmitted).stream()
                .filter(action -> action.status().equals(toStatus))
                .findFirst()
                .orElse(null);
    }

    private static Action action(String status, String label, Kind kind, AfterSaleOperateTypeEnum operateType) {
        return new Action(status, label, kind, operateType, false);
    }

    private static Action action(String status, String label, Kind kind, AfterSaleOperateTypeEnum operateType,
                                  boolean requiresReturnSubmitted) {
        return new Action(status, label, kind, operateType, requiresReturnSubmitted);
    }

    private static String key(String type, String status) {
        return type + "|" + status;
    }
}
