package ecommerce.log;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解（通用审计）。
 * <p>
 * 打标在 Controller 或 Service 的实现类方法上，由 {@link OperationLogAspect} 统一采集
 * 「谁 / 何时 / 哪个 IP / 哪个接口 / 什么参数 / 成功还是失败 / 耗时多久」写入 operation_log。
 * <p>
 * 注解只描述「这是一次什么操作」，具体参数值用 SpEL 表达式取：
 * <pre>
 * &#64;OperationLog(module = "aftersales", action = "REVIEW", bizId = "#id")
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 业务模块，如 aftersales / product / order */
    String module();

    /** 动作，如 REVIEW / DELETE / UPDATE_PRICE */
    String action();

    /** 业务对象 ID 的 SpEL 表达式，如 "#id"；取不到留空 */
    String bizId() default "";

    /** 补充说明的 SpEL 表达式；取不到留空 */
    String detail() default "";

    /** 是否记录请求参数（JSON）。敏感接口（如改密码）应关掉 */
    boolean recordParams() default true;
}
