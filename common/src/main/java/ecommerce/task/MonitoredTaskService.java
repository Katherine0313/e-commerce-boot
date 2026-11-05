package ecommerce.task;

import ecommerce.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 手动/定时执行定时任务的注册表：收集当前服务里所有 {@link MonitoredTaskRunner} 实现。
 * <p>
 * 每次执行都写 admin_monitor_task_log（表不存在或数据库不可用时跳过，不影响任务）。
 */
@Slf4j
@Service
public class MonitoredTaskService {

    private final Map<String, MonitoredTaskRunner> runners = new LinkedHashMap<>();
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private volatile TaskLogStore taskLogStore;

    public MonitoredTaskService(List<MonitoredTaskRunner> runnerList,
                                ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        if (runnerList != null) {
            runnerList.forEach(runner -> runners.put(runner.taskName(), runner));
        }
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    /** 当前服务支持执行的任务名 */
    public List<String> supportedTasks() {
        return List.copyOf(runners.keySet());
    }

    /** 后台手动执行（trigger_type=2） */
    public void run(String name) {
        run(name, 2, null);
    }

    /** 定时触发（trigger_type=1），taskId 来自 admin_monitor_task */
    public void runScheduled(String name, Long taskId) {
        run(name, 1, taskId);
    }

    public void run(String name, int triggerType, Long taskId) {
        MonitoredTaskRunner runner = runners.get(name);
        if (runner == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "该任务不支持在后台手动执行");
        }
        LocalDateTime startedAt = LocalDateTime.now();
        long startMs = System.currentTimeMillis();
        TaskLogStore store = store();
        Long logId = store == null ? null : store.start(taskId, name, triggerType, startedAt);
        boolean success = true;
        String message = null;
        try {
            runner.run();
        } catch (RuntimeException e) {
            success = false;
            message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw e;
        } finally {
            if (store != null) {
                store.finish(logId, success, message, System.currentTimeMillis() - startMs,
                        LocalDateTime.now());
            }
        }
    }

    private TaskLogStore store() {
        if (taskLogStore != null) {
            return taskLogStore;
        }
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            return null;
        }
        taskLogStore = new TaskLogStore(jdbcTemplate);
        return taskLogStore;
    }
}
