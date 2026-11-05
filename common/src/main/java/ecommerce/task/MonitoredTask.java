package ecommerce.task;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 定时任务监控标记。
 * <p>
 * 打在 {@code @Scheduled} 方法上，切面会把「最近一次开始/结束/耗时/结果」写入 Redis：
 * {@code monitor:task:last:{name}}，供后台「系统监控 → 定时任务」页面展示。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MonitoredTask {

    /** 任务唯一标识，如 logCleanup / closeExpiredOrders */
    String name();

    /** 任务说明 */
    String description() default "";

    /** 执行周期描述，如 "每 60 秒" / "0 0 3 * * ?" */
    String cron() default "";

    /** 是否允许在后台手动执行；只对幂等、低风险任务开放 */
    boolean allowManualRun() default false;
}
