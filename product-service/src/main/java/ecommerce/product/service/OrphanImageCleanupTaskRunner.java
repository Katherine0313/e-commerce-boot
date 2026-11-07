package ecommerce.product.service;

import ecommerce.task.MonitoredTaskRunner;
import org.springframework.stereotype.Component;

/**
 * 孤儿文件清理任务的手动执行入口，供后台「系统监控 → 定时任务」调用。
 * <p>
 * 定时触发由 {@code admin_monitor_task} 表 + common 的 DynamicTaskScheduler 驱动，
 * task_group 填 product-service，调度器只会把本服务有的任务派给自己。
 */
@Component
public class OrphanImageCleanupTaskRunner implements MonitoredTaskRunner {

    private final OrphanImageCleanupTask orphanImageCleanupTask;

    public OrphanImageCleanupTaskRunner(OrphanImageCleanupTask orphanImageCleanupTask) {
        this.orphanImageCleanupTask = orphanImageCleanupTask;
    }

    @Override
    public String taskName() {
        return "orphanImageCleanup";
    }

    @Override
    public void run() {
        orphanImageCleanupTask.clean();
    }
}
