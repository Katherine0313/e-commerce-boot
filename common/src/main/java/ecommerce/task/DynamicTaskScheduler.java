package ecommerce.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 动态定时任务调度器（每个业务服务各跑一份）。
 * <p>
 * 从 admin_monitor_task 读取启用任务，只调度「本服务有同名 MonitoredTaskRunner」的行，
 * 其余行忽略。每 30 秒增量同步一次：新增/修改 cron/启停都会在 30 秒内生效，无需重启。
 * <p>
 * 仅当配置 monitor.dynamic-task.enabled=true 时装配（目前只有 auth-service / order-service
 * 需要），避免网关、file-service 这类没有数据库的服务启动失败。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "monitor.dynamic-task.enabled", havingValue = "true")
public class DynamicTaskScheduler implements SmartLifecycle {

    private static final long RELOAD_INTERVAL_MS = 30_000L;
    private static final long TABLE_WARN_INTERVAL_MS = 300_000L;

    private static final String SELECT_SQL = """
            SELECT id, task_name, task_type, schedule_value, concurrent
              FROM admin_monitor_task
             WHERE status = 1
            """;

    private final JdbcTemplate jdbcTemplate;
    private final MonitoredTaskService monitoredTaskService;
    private final Map<Long, ScheduledEntry> scheduled = new ConcurrentHashMap<>();
    private final Set<Long> executing = ConcurrentHashMap.newKeySet();
    private final AtomicLong lastTableWarnAt = new AtomicLong(0);

    private ThreadPoolTaskScheduler scheduler;
    private ScheduledFuture<?> reloadFuture;
    private volatile boolean running;

    public DynamicTaskScheduler(JdbcTemplate jdbcTemplate,
                                MonitoredTaskService monitoredTaskService) {
        this.jdbcTemplate = jdbcTemplate;
        this.monitoredTaskService = monitoredTaskService;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("dynamic-task-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        reload();
        reloadFuture = scheduler.scheduleWithFixedDelay(
                this::reload, Duration.ofMillis(RELOAD_INTERVAL_MS));
        running = true;
        log.info("动态定时任务调度器已启动");
    }

    @Override
    public void stop() {
        running = false;
        if (reloadFuture != null) {
            reloadFuture.cancel(false);
        }
        scheduled.values().forEach(entry -> entry.future().cancel(false));
        scheduled.clear();
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** 增量同步数据库配置：签名不变的任务不动，变了的重排，删掉/停用的取消 */
    private void reload() {
        List<TaskRow> rows;
        try {
            rows = jdbcTemplate.query(SELECT_SQL, (rs, rowNum) -> new TaskRow(
                    rs.getLong("id"),
                    rs.getString("task_name"),
                    rs.getInt("task_type"),
                    rs.getString("schedule_value"),
                    rs.getInt("concurrent")));
        } catch (RuntimeException e) {
            warnTableOnce(e);
            return;
        }
        Set<Long> seen = new HashSet<>();
        Set<String> supported = new HashSet<>(monitoredTaskService.supportedTasks());
        for (TaskRow row : rows) {
            if (!supported.contains(row.taskName())) {
                // 不是本服务的任务，交给对应服务去调度
                continue;
            }
            seen.add(row.id());
            String signature = row.taskType() + "|" + row.scheduleValue() + "|" + row.concurrent();
            ScheduledEntry current = scheduled.get(row.id());
            if (current != null && current.signature().equals(signature)) {
                continue;
            }
            if (current != null) {
                current.future().cancel(false);
            }
            ScheduledFuture<?> future = schedule(row);
            if (future != null) {
                scheduled.put(row.id(), new ScheduledEntry(signature, future));
            } else {
                scheduled.remove(row.id());
            }
        }
        scheduled.entrySet().removeIf(entry -> {
            if (seen.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().future().cancel(false);
            return true;
        });
    }

    private ScheduledFuture<?> schedule(TaskRow row) {
        Runnable task = () -> execute(row);
        try {
            if (row.taskType() == 1) {
                CronExpression.parse(row.scheduleValue());
                return scheduler.schedule(task, new CronTrigger(row.scheduleValue()));
            }
            long millis = parseMillis(row.scheduleValue());
            if (millis <= 0) {
                log.warn("任务 {} 的间隔配置非法: {}", row.taskName(), row.scheduleValue());
                return null;
            }
            if (row.taskType() == 3) {
                return scheduler.scheduleAtFixedRate(task, Duration.ofMillis(millis));
            }
            return scheduler.scheduleWithFixedDelay(task, Duration.ofMillis(millis));
        } catch (RuntimeException e) {
            log.warn("任务 {} 注册失败: {}", row.taskName(), e.getMessage());
            return null;
        }
    }

    private void execute(TaskRow row) {
        if (row.concurrent() == 1 && !executing.add(row.id())) {
            log.debug("任务 {} 上一轮仍在执行，跳过本次", row.taskName());
            return;
        }
        try {
            monitoredTaskService.runScheduled(row.taskName(), row.id());
        } catch (Exception e) {
            log.warn("定时任务执行失败: {} - {}", row.taskName(), e.getMessage());
        } finally {
            executing.remove(row.id());
        }
    }

    private long parseMillis(String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void warnTableOnce(RuntimeException e) {
        long now = System.currentTimeMillis();
        long last = lastTableWarnAt.get();
        if (now - last >= TABLE_WARN_INTERVAL_MS && lastTableWarnAt.compareAndSet(last, now)) {
            log.warn("读取动态任务配置失败（确认已执行 docs/sql/20261105_system_monitor_p2.sql）: {}",
                    e.getMessage());
        }
    }

    private record TaskRow(long id, String taskName, int taskType,
                           String scheduleValue, int concurrent) {
    }

    private record ScheduledEntry(String signature, ScheduledFuture<?> future) {
    }
}
