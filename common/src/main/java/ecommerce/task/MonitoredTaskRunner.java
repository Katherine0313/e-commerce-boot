package ecommerce.task;

/**
 * 可手动执行的定时任务入口。
 * <p>
 * 各服务把需要开放手动执行的任务实现成该接口的 Bean，system-service 通过服务名转发调用。
 * 实现类里调用被 {@link MonitoredTask} 标记的方法，保证执行记录与定时触发一致。
 */
public interface MonitoredTaskRunner {

    /** 与 {@link MonitoredTask#name()} 一致 */
    String taskName();

    /** 执行一次 */
    void run();
}
