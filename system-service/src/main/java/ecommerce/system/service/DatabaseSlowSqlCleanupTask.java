package ecommerce.system.service;

import ecommerce.task.MonitoredTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.function.IntSupplier;

/**
 * 慢 SQL 记录清理：每天 03:20 删除超过保留期（默认 7 天）的记录。
 * <p>
 * 只注册在 system-service，避免多服务重复删；分批删，防止长事务锁表。
 * 清理失败只记 warn，不影响业务。
 */
@Slf4j
@Component
public class DatabaseSlowSqlCleanupTask {

    private static final int BATCH_SIZE = 1000;
    private static final int MAX_BATCHES = 200;

    private final JdbcTemplate jdbcTemplate;
    private final int retainDays;

    public DatabaseSlowSqlCleanupTask(JdbcTemplate jdbcTemplate,
                                      @Value("${monitor.sql.retain-days:7}") int retainDays) {
        this.jdbcTemplate = jdbcTemplate;
        this.retainDays = retainDays;
    }

    /** 执行周期由后台动态任务配置（admin_monitor_task）控制，默认每天 03:20 */
    @MonitoredTask(name = "dbSlowSqlCleanup", description = "清理超过保留期的慢 SQL 记录",
            cron = "0 20 3 * * ?", allowManualRun = true)
    public void clean() {
        // 用 Java 时间而不是 NOW()：DB 时区与业务时区可能不一致
        LocalDateTime before = LocalDateTime.now().minusDays(retainDays);
        int deleted = 0;
        try {
            deleted = deleteInBatches(() -> jdbcTemplate.update(
                    "DELETE FROM admin_db_slow_sql WHERE created_at < ? LIMIT ?",
                    before, BATCH_SIZE));
            log.info("慢 SQL 清理完成: 阈值 {}, 删除 {} 条", before, deleted);
        } catch (Exception e) {
            log.warn("慢 SQL 清理失败: {}", e.getMessage(), e);
        }
    }

    private int deleteInBatches(IntSupplier deleter) {
        int total = 0;
        for (int i = 0; i < MAX_BATCHES; i++) {
            int deleted = deleter.getAsInt();
            total += deleted;
            if (deleted < BATCH_SIZE) {
                return total;
            }
        }
        log.warn("慢 SQL 清理达到批次上限 {}，剩余部分留到下次任务处理", MAX_BATCHES);
        return total;
    }
}
