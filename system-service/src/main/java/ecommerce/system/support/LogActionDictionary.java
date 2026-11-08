package ecommerce.system.support;

import java.util.Map;

/**
 * 操作日志动作字典。
 * <p>
 * 数据库里存的是英文动作码（USER_ADD 等），查询/导出时统一翻译成中文，
 * 避免前端直接渲染英文。新增动作时在这里补一行，找不到的动作原样返回。
 */
public final class LogActionDictionary {

    private static final Map<String, String> LABELS = Map.ofEntries(
            // 售后
            Map.entry("REVIEW", "售后审核"),
            Map.entry("DELETE", "删除售后"),
            // 发货物流
            Map.entry("SHIP", "订单发货"),
            Map.entry("RESEND", "订单补发"),
            Map.entry("UPDATE_DELIVERY", "修改物流"),
            Map.entry("EXPRESS_SAVE", "新增快递公司"),
            Map.entry("EXPRESS_UPDATE", "修改快递公司"),
            Map.entry("EXPRESS_REMOVE", "删除快递公司"),
            // 系统管理
            Map.entry("USER_ADD", "新增管理员"),
            Map.entry("USER_EDIT", "修改管理员"),
            Map.entry("USER_REMOVE", "删除管理员"),
            Map.entry("RESET_PWD", "重置密码"),
            Map.entry("ROLE_ADD", "新增角色"),
            Map.entry("ROLE_EDIT", "修改角色"),
            Map.entry("ROLE_REMOVE", "删除角色"),
            Map.entry("ROLE_ASSIGN", "分配角色菜单"),
            Map.entry("MENU_ADD", "新增菜单"),
            Map.entry("MENU_EDIT", "修改菜单"),
            Map.entry("MENU_REMOVE", "删除菜单"),
            Map.entry("LOG_EXPORT", "导出日志"),
            Map.entry("LOG_CLEAN", "清理日志"),
            // 商品
            Map.entry("PRODUCT_ADD", "新增商品"),
            Map.entry("PRODUCT_EDIT", "修改商品"),
            Map.entry("PRODUCT_REMOVE", "删除商品"),
            Map.entry("STOCK_ALERT", "设置库存预警"),
            // 订单
            Map.entry("ORDER_UPDATE", "修改订单"),
            Map.entry("ORDER_CANCEL", "取消订单"),
            Map.entry("ORDER_REMOVE", "删除订单"),
            Map.entry("ORDER_BATCH_REMOVE", "批量删除订单"),
            // 营销
            Map.entry("COUPON_ADD", "新增优惠券"),
            Map.entry("COUPON_EDIT", "修改优惠券"),
            Map.entry("COUPON_STOP", "停用优惠券"),
            Map.entry("COUPON_REMOVE", "删除优惠券"),
            Map.entry("COUPON_GRANT", "发放优惠券"),
            Map.entry("BANNER_ADD", "新增 Banner"),
            Map.entry("BANNER_EDIT", "修改 Banner"),
            Map.entry("BANNER_STATUS", "Banner 上下架"),
            Map.entry("BANNER_REMOVE", "删除 Banner"),
            Map.entry("ARTICLE_ADD", "新增文章"),
            Map.entry("ARTICLE_EDIT", "修改文章"),
            Map.entry("ARTICLE_STATUS", "文章上下架"),
            Map.entry("ARTICLE_REMOVE", "删除文章"));

    private LogActionDictionary() {
    }

    /**
     * 动作码转中文；未收录的码原样返回。
     */
    public static String label(String action) {
        if (action == null || action.isBlank()) {
            return action;
        }
        return LABELS.getOrDefault(action, action);
    }
}
