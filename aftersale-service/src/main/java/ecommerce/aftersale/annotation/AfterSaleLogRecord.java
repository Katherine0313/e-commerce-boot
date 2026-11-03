package ecommerce.aftersale.annotation;

import ecommerce.aftersale.enums.AfterSaleOperateTypeEnum;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 售后变更流水注解。
 * <p>
 * 打在售后 Service 的**实现类**方法上（不是接口），由 {@code aspect.AfterSaleLogAspect} 采集
 * 「前状态 → 后状态 + 操作类型 + 文案」写入 after_sale_log。
 * <p>
 * 注解只打在实现类上：注解打在接口上，JDK 动态代理下会静默失效。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AfterSaleLogRecord {

    /** 操作类型。dynamic = true 时忽略这个值，改由状态机按 (类型, 前状态, 后状态) 推导 */
    AfterSaleOperateTypeEnum operateType() default AfterSaleOperateTypeEnum.USER_SUBMIT;

    /**
     * 是否按状态机动态推导操作类型。
     * <p>
     * 审核（review）场景用 true —— 「同意仅退款 / 同意退货退款 / 同意换货 / 确认退款 / 换货完成」
     * 都走同一个接口，只有目标状态不同，操作类型必须由状态机推导。
     */
    boolean dynamic() default false;

    /** 售后单 ID 的 SpEL 表达式，默认取第一个参数（create 这种新建的取不到，靠返回值兜底） */
    String afterSaleId() default "#p0";

    /** 商家内部备注的 SpEL 表达式，只有后台操作才有 */
    String merchantRemark() default "";

    /** 操作人类型 */
    OperatorType operator() default OperatorType.AUTO;

    /** 操作人类型 */
    enum OperatorType {
        /** 按当前登录态自动判断：admin → ADMIN，user → BUYER */
        AUTO,
        /** 买家（C 端） */
        BUYER,
        /** 后台管理员 */
        ADMIN
    }
}
