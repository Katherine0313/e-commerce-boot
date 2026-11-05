package ecommerce.auth.service;

import ecommerce.task.MonitoredTaskRunner;
import org.springframework.stereotype.Component;

/**
 * 日志清理任务的手动执行入口，供后台「系统监控 → 定时任务」调用。
 */
@Component
public class LogCleanupTaskRunner implements MonitoredTaskRunner {

    private final LogCleanupTask logCleanupTask;

    public LogCleanupTaskRunner(LogCleanupTask logCleanupTask) {
        this.logCleanupTask = logCleanupTask;
    }

    @Override
    public String taskName() {
        return "logCleanup";
    }

    @Override
    public void run() {
        logCleanupTask.clean();
    }
}
