package ecommerce.monitor.sql;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 慢 SQL 采集拦截器。
 * <p>
 * 在 MyBatis 的 JDBC 调用点计时，能同时拿到 Mapper 方法、真实 SQL、耗时与影响行数。
 * 只记录超过阈值的语句，不记录参数值（只保留 {@code ?} 占位），避免敏感数据落库。
 * <p>
 * 两个 query 重载都要拦：分页/二级缓存的调用链会走带 CacheKey + BoundSql 的那个，只拦第一个会漏。
 */
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                        CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class SqlMonitorInterceptor implements Interceptor {

    /** 递归防护：写入链路万一走回 MyBatis，也不会自激 */
    private static final ThreadLocal<Boolean> RECORDING = new ThreadLocal<>();

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:[^']|'')*'");
    private static final Pattern NUMBER_LITERAL = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b");
    private static final Pattern IN_LIST = Pattern.compile("(?i)\\bIN\\s*\\(\\s*\\?(?:\\s*,\\s*\\?)*\\s*\\)");

    private final SqlMonitorProperties properties;
    private final SqlMonitorRecorder recorder;
    private final String serviceName;

    public SqlMonitorInterceptor(SqlMonitorProperties properties,
                                 SqlMonitorRecorder recorder,
                                 String serviceName) {
        this.properties = properties;
        this.recorder = recorder;
        this.serviceName = serviceName;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (Boolean.TRUE.equals(RECORDING.get())) {
            return invocation.proceed();
        }
        long start = System.nanoTime();
        Object result = null;
        boolean success = false;
        try {
            result = invocation.proceed();
            success = true;
            return result;
        } finally {
            long costMs = (System.nanoTime() - start) / 1_000_000L;
            if (costMs >= properties.getSlowThresholdMs()) {
                try {
                    capture(invocation, costMs, success ? result : null);
                } catch (RuntimeException e) {
                    // 采集自身异常绝不影响业务
                }
            }
        }
    }

    private void capture(Invocation invocation, long costMs, Object result) {
        Object[] args = invocation.getArgs();
        if (args == null || args.length == 0 || !(args[0] instanceof MappedStatement statement)) {
            return;
        }
        BoundSql boundSql;
        try {
            Object parameter = args.length > 1 ? args[1] : null;
            boundSql = args.length >= 6 && args[5] instanceof BoundSql cached
                    ? cached : statement.getBoundSql(parameter);
        } catch (RuntimeException e) {
            return;
        }
        String sql = normalize(boundSql == null ? null : boundSql.getSql());
        if (sql.isEmpty()) {
            return;
        }
        Integer rowCount = rowCount(result);
        RECORDING.set(Boolean.TRUE);
        try {
            recorder.record(serviceName, truncate(statement.getId(), 255),
                    statement.getSqlCommandType().name(), md5(sql), truncate(sql, properties.getMaxSqlLength()),
                    costMs, rowCount);
        } finally {
            RECORDING.remove();
        }
    }

    /** query 取结果条数，update 取影响行数；未知返回 null */
    private Integer rowCount(Object result) {
        if (result instanceof List<?> list) {
            return list.size();
        }
        if (result instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    /**
     * SQL 归一化：压空白 → 字符串字面量 → 数字字面量 → IN 变长列表。
     * 同一语句不同参数归到同一个指纹，聚合 Top 榜才有意义。
     */
    String normalize(String sql) {
        if (sql == null || sql.isBlank()) {
            return "";
        }
        String text = WHITESPACE.matcher(sql).replaceAll(" ").trim();
        text = STRING_LITERAL.matcher(text).replaceAll("?");
        text = NUMBER_LITERAL.matcher(text).replaceAll("?");
        text = IN_LIST.matcher(text).replaceAll("IN (?)");
        return text;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString().toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            // JDK 必须提供 MD5；真没有就退化用 hashCode，聚合仍然可用
            return String.format("%032x", value.hashCode());
        }
    }
}
