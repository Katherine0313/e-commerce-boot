package ecommerce.log;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 默认的操作日志写入实现：JdbcTemplate 直写 operation_log，异步。
 * <p>
 * 为什么用 JdbcTemplate 而不是 MyBatis：这张表是通用基建，挂在 common 里。
 * 各服务的 Mapper 扫描路径互不相同（@MapperScan("ecommerce.xxx.mapper")），
 * 在 common 里塞一个 @Mapper 会导致各服务都要改扫描配置。JdbcTemplate 直接写，不动任何服务的 Mapper。
 * <p>
 * 异步：写日志失败、线程池满都只记 warn，绝不影响业务（审计语义）。
 * 异步也天然脱离了业务事务 —— 业务回滚了日志已写，属正常审计行为。
 */
@Slf4j
@Component
public class JdbcOperationLogRecorder implements OperationLogRecorder {

    private static final String INSERT_SQL = """
            INSERT INTO admin_operation_log
              (user_id, user_type, username, module, action, biz_id, detail, method, uri, ip, params, success, error_msg, cost_ms, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final ThreadPoolTaskExecutor executor;

    public JdbcOperationLogRecorder(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                                    ThreadPoolTaskExecutor operationLogExecutor) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.executor = operationLogExecutor;
    }

    @Override
    public void save(OperationLogEntry entry) {
        if (entry == null) {
            return;
        }
        try {
            executor.execute(() -> doSave(entry));
        } catch (Exception e) {
            // 线程池都拒绝不了（极端情况），至少别让业务挂掉
            log.warn("操作日志提交线程池失败: module={}, action={}, err={}", entry.getModule(), entry.getAction(), e.getMessage());
        }
    }

    /** 真正落库，异常一律吞掉 */
    private void doSave(OperationLogEntry entry) {
        try {
            // 有些服务（如 file-service）没接 JDBC，没有 JdbcTemplate 就跳过，不能让它们启动失败
            JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
            if (jdbcTemplate == null) {
                return;
            }
            jdbcTemplate.update(INSERT_SQL,
                    entry.getUserId(),
                    entry.getUserType(),
                    entry.getUsername(),
                    entry.getModule(),
                    entry.getAction(),
                    entry.getBizId(),
                    entry.getDetail(),
                    entry.getMethod(),
                    entry.getUri(),
                    entry.getIp(),
                    entry.getParams(),
                    entry.isSuccess() ? 1 : 0,
                    entry.getErrorMsg(),
                    entry.getCostMs(),
                    entry.getCreatedAt() != null ? entry.getCreatedAt() : LocalDateTime.now());
        } catch (Exception e) {
            // 审计日志写失败只记 warn：表建了没、DB 抖动，都不该让业务失败
            log.warn("操作日志写入失败: module={}, action={}, err={}", entry.getModule(), entry.getAction(), e.getMessage());
        }
    }
}
