package ecommerce.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 定时任务执行日志落库（admin_monitor_task_log）。
 * <p>
 * 用 JdbcTemplate 而不是 MyBatis：日志写入不能因为某个服务的 Mapper 扫描配置缺失而失效。
 * 表不存在、数据库不可用时只记日志，绝不能影响任务本身。
 */
@Slf4j
public class TaskLogStore {

    private static final String INSERT_SQL = """
            INSERT INTO admin_monitor_task_log
              (task_id, task_name, trigger_type, status, cost_ms, started_at, create_time)
            VALUES (?, ?, ?, 1, 0, ?, ?)
            """;
    private static final String FINISH_SQL = """
            UPDATE admin_monitor_task_log
               SET status = ?, error_msg = ?, cost_ms = ?, finished_at = ?
             WHERE id = ?
            """;
    private static final String FIND_TASK_ID_SQL =
            "SELECT id FROM admin_monitor_task WHERE task_name = ? LIMIT 1";

    private final JdbcTemplate jdbcTemplate;

    public TaskLogStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 开始执行：写入一条初始记录，返回日志 ID（失败返回 null） */
    public Long start(Long taskId, String taskName, int triggerType, LocalDateTime startedAt) {
        try {
            Long resolvedTaskId = taskId == null ? findTaskId(taskName) : taskId;
            KeyHolder keyHolder = new GeneratedKeyHolder();
            Timestamp started = Timestamp.valueOf(startedAt);
            Timestamp created = Timestamp.valueOf(LocalDateTime.now());
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, resolvedTaskId == null ? 0L : resolvedTaskId);
                ps.setString(2, taskName);
                ps.setInt(3, triggerType);
                ps.setTimestamp(4, started);
                ps.setTimestamp(5, created);
                return ps;
            }, keyHolder);
            Number key = keyHolder.getKey();
            return key == null ? null : key.longValue();
        } catch (RuntimeException e) {
            log.debug("写入任务执行日志失败, taskName={}: {}", taskName, e.getMessage());
            return null;
        }
    }

    /** 执行结束：补成功/失败、耗时、结束时间 */
    public void finish(Long logId, boolean success, String errorMsg, long costMs,
                       LocalDateTime finishedAt) {
        if (logId == null) {
            return;
        }
        try {
            String message = errorMsg;
            if (message != null && message.length() > 1024) {
                message = message.substring(0, 1024);
            }
            jdbcTemplate.update(FINISH_SQL,
                    success ? 1 : 0, message, (int) Math.min(Integer.MAX_VALUE, costMs),
                    Timestamp.valueOf(finishedAt), logId);
        } catch (RuntimeException e) {
            log.debug("更新任务执行日志失败, logId={}: {}", logId, e.getMessage());
        }
    }

    private Long findTaskId(String taskName) {
        try {
            return jdbcTemplate.query(FIND_TASK_ID_SQL, rs -> rs.next() ? rs.getLong(1) : null, taskName);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
