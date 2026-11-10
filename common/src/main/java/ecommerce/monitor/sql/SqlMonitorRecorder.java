package ecommerce.monitor.sql;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 慢 SQL 记录器：限流 + 异步写入 {@code admin_db_slow_sql}。
 * <p>
 * 与操作日志的取舍刻意不同：审计日志用 CallerRuns，宁可阻塞也要保住；
 * 慢 SQL 是采样数据，队列满/超限流就丢弃并计数，绝不阻塞业务线程。
 * <p>
 * 写入走 JdbcTemplate（不走 MyBatis），天然绕过拦截器，不会自激。
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.apache.ibatis.plugin.Interceptor")
@ConditionalOnProperty(prefix = "monitor.sql", name = "enabled", matchIfMissing = true)
public class SqlMonitorRecorder {

    private static final String INSERT_SQL = """
            INSERT INTO admin_db_slow_sql
              (service_name, mapper_method, sql_type, sql_fingerprint, sql_text, cost_ms, row_count, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final ThreadPoolTaskExecutor executor;
    private final SqlMonitorMetrics metrics;
    private final SqlMonitorProperties properties;

    /** 当前分钟窗口（epoch 分钟）与窗口内已入库条数 */
    private final AtomicLong windowMinute = new AtomicLong(-1);
    private final AtomicInteger windowCount = new AtomicInteger();

    public SqlMonitorRecorder(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                              @Qualifier("sqlMonitorExecutor") ThreadPoolTaskExecutor executor,
                              SqlMonitorMetrics metrics,
                              SqlMonitorProperties properties) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.executor = executor;
        this.metrics = metrics;
        this.properties = properties;
    }

    public void record(String serviceName, String mapperMethod, String sqlType,
                       String sqlFingerprint, String sqlText, long costMs, Integer rowCount) {
        if (!tryAcquire()) {
            metrics.incrementDropped();
            return;
        }
        try {
            executor.execute(() -> doSave(serviceName, mapperMethod, sqlType,
                    sqlFingerprint, sqlText, costMs, rowCount));
            metrics.incrementRecorded();
        } catch (RuntimeException e) {
            // 线程池已关闭等极端情况：丢一条监控记录，不影响业务
            metrics.incrementDropped();
        }
    }

    /** 每分钟窗口限流；跨分钟时重置计数（并发下偶尔多放几条，可接受） */
    private boolean tryAcquire() {
        int max = Math.max(properties.getMaxPerMinute(), 1);
        long minute = System.currentTimeMillis() / 60_000L;
        long current = windowMinute.get();
        if (current != minute && windowMinute.compareAndSet(current, minute)) {
            windowCount.set(0);
        }
        return windowCount.incrementAndGet() <= max;
    }

    private void doSave(String serviceName, String mapperMethod, String sqlType,
                        String sqlFingerprint, String sqlText, long costMs, Integer rowCount) {
        try {
            JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
            if (jdbcTemplate == null) {
                return;
            }
            jdbcTemplate.update(INSERT_SQL, serviceName, mapperMethod, sqlType,
                    sqlFingerprint, sqlText, (int) Math.min(costMs, Integer.MAX_VALUE),
                    rowCount, LocalDateTime.now());
        } catch (Exception e) {
            // 监控写失败只记 warn：表没建、DB 抖动都不该影响业务
            log.warn("慢 SQL 写入失败: service={}, mapper={}, err={}",
                    serviceName, mapperMethod, e.getMessage());
        }
    }
}
