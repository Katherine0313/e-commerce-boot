package ecommerce.system.service;

import ecommerce.task.MonitoredTaskRunner;
import org.springframework.stereotype.Component;

/**
 * 慢 SQL 清理任务的手动执行入口（后台「系统监控 → 定时任务」）。
 * <p>
 * taskName 必须与 {@link DatabaseSlowSqlCleanupTask} 上 {@code @MonitoredTask.name}
 * 以及 admin_monitor_task.task_name 三处一致，否则调度器扫不到。
 */
@Component
public class DatabaseSlowSqlCleanupTaskRunner implements MonitoredTaskRunner {

    private final DatabaseSlowSqlCleanupTask cleanupTask;

    public DatabaseSlowSqlCleanupTaskRunner(DatabaseSlowSqlCleanupTask cleanupTask) {
        this.cleanupTask = cleanupTask;
    }

    @Override
    public String taskName() {
        return "dbSlowSqlCleanup";
    }

    @Override
    public void run() {
        cleanupTask.clean();
    }
}
