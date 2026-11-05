package ecommerce.monitor.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每天凌晨 4 点清理 30 天前的定时任务执行记录。
 */
@Component
public class TaskLogCleanupJob {

    private final TaskMonitorService taskMonitorService;

    public TaskLogCleanupJob(TaskMonitorService taskMonitorService) {
        this.taskMonitorService = taskMonitorService;
    }

    @Scheduled(cron = "0 0 4 * * ?")
    public void clean() {
        taskMonitorService.cleanupLogs();
    }
}
