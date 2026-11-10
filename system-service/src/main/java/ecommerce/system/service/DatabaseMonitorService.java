package ecommerce.system.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import ecommerce.common.PageVO;
import ecommerce.monitor.sql.SqlMonitorMetrics;
import ecommerce.monitor.sql.SqlMonitorProperties;
import ecommerce.system.vo.DbTableSizeVO;
import ecommerce.system.vo.SlowSqlTopVO;
import ecommerce.system.vo.SlowSqlTrendVO;
import ecommerce.system.vo.SlowSqlVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库监控查询（system-service 单点）。
 * <p>
 * 数据来源三块：
 * 1) 慢 SQL 表 admin_db_slow_sql（各服务 common 拦截器写入）；
 * 2) MySQL 全局状态 / information_schema（JdbcTemplate 直查）；
 * 3) 各服务 actuator 的 Hikari 指标（ServiceMetricsService 聚合）。
 * <p>
 * 口径：慢 SQL 耗时是 Java 侧看到的执行耗时（含网络往返），不含连接池等待；
 * 连接等待看 pending。
 */
@Slf4j
@Service
public class DatabaseMonitorService {

    private static final String QPS_KEY = "monitor:db:status:last";

    private static final String STATUS_SQL = """
            SHOW GLOBAL STATUS WHERE Variable_name IN
              ('Threads_connected', 'Threads_running', 'Questions', 'Slow_queries',
               'Innodb_buffer_pool_reads', 'Innodb_buffer_pool_read_requests')
            """;

    private static final String VARIABLES_SQL = """
            SHOW VARIABLES WHERE Variable_name IN ('max_connections', 'slow_query_log', 'long_query_time')
            """;

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final StringRedisTemplate stringRedisTemplate;
    private final ServiceMetricsService serviceMetricsService;
    private final SqlMonitorMetrics sqlMonitorMetrics;
    private final SqlMonitorProperties sqlMonitorProperties;

    public DatabaseMonitorService(JdbcTemplate jdbcTemplate,
                                  DataSource dataSource,
                                  StringRedisTemplate stringRedisTemplate,
                                  ServiceMetricsService serviceMetricsService,
                                  SqlMonitorMetrics sqlMonitorMetrics,
                                  SqlMonitorProperties sqlMonitorProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.stringRedisTemplate = stringRedisTemplate;
        this.serviceMetricsService = serviceMetricsService;
        this.sqlMonitorMetrics = sqlMonitorMetrics;
        this.sqlMonitorProperties = sqlMonitorProperties;
    }

    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("slowThresholdMs", sqlMonitorProperties.getSlowThresholdMs());
        result.put("retainDays", sqlMonitorProperties.getRetainDays());
        result.put("collector", Map.of(
                "recorded", sqlMonitorMetrics.getRecorded(),
                "dropped", sqlMonitorMetrics.getDropped()));

        Map<String, Object> pool = ownPool();
        result.put("pool", pool);

        try {
            Map<String, String> status = queryPairs(STATUS_SQL);
            Map<String, String> variables = queryPairs(VARIABLES_SQL);
            long connected = parseLong(status.get("Threads_connected"));
            long maxConnections = parseLong(variables.get("max_connections"));
            long reads = parseLong(status.get("Innodb_buffer_pool_reads"));
            long readRequests = parseLong(status.get("Innodb_buffer_pool_read_requests"));

            result.put("threadsConnected", connected);
            result.put("threadsRunning", parseLong(status.get("Threads_running")));
            result.put("maxConnections", maxConnections);
            result.put("connectionUsagePercent", maxConnections > 0
                    ? round(connected * 100.0 / maxConnections) : 0);
            result.put("slowQueriesTotal", parseLong(status.get("Slow_queries")));
            result.put("slowQueryLog", "ON".equalsIgnoreCase(variables.get("slow_query_log")));
            result.put("longQueryTime", variables.getOrDefault("long_query_time", "-"));
            result.put("bufferHitRate", readRequests > 0
                    ? round((1 - reads * 1.0 / readRequests) * 100) : 100);
            result.put("qps", qps(status.get("Questions")));
        } catch (RuntimeException e) {
            log.warn("读取 MySQL 全局状态失败: {}", e.getMessage());
            result.put("error", "读取 MySQL 状态失败：" + e.getMessage());
        }

        try {
            Long todaySlow = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM admin_db_slow_sql WHERE created_at >= ?",
                    Long.class, LocalDate.now().atStartOfDay());
            result.put("todaySlowSql", todaySlow == null ? 0 : todaySlow);

            Map<String, Object> size = jdbcTemplate.queryForMap("""
                    SELECT COUNT(*) AS table_count,
                           COALESCE(SUM(DATA_LENGTH + INDEX_LENGTH), 0) AS total_length
                      FROM information_schema.TABLES
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
                    """);
            long totalLength = ((Number) size.get("total_length")).longValue();
            result.put("tableCount", ((Number) size.get("table_count")).longValue());
            result.put("totalSize", totalLength);
            result.put("totalSizeHuman", DatabaseAdminService.humanSize(totalLength));
        } catch (RuntimeException e) {
            log.warn("统计慢 SQL / 表容量失败: {}", e.getMessage());
            result.put("todaySlowSql", 0);
            result.put("tableCount", 0);
            result.put("totalSize", 0);
            result.put("totalSizeHuman", "-");
        }
        return result;
    }

    /** 慢 SQL 明细分页 */
    public PageVO<SlowSqlVO> slowSqls(String service, Integer minCost, Integer hours,
                                      int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 200);
        LocalDateTime since = LocalDateTime.now().minusHours(safeHours(hours));

        StringBuilder where = new StringBuilder(" WHERE created_at >= ?");
        List<Object> args = new ArrayList<>();
        args.add(since);
        if (service != null && !service.isBlank()) {
            where.append(" AND service_name = ?");
            args.add(service.trim());
        }
        if (minCost != null && minCost > 0) {
            where.append(" AND cost_ms >= ?");
            args.add(minCost);
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_db_slow_sql" + where, Long.class, args.toArray());
        long totalCount = total == null ? 0 : total;

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((safePage - 1) * safeSize);
        pageArgs.add(safeSize);
        List<SlowSqlVO> list = jdbcTemplate.query("""
                        SELECT id, service_name, mapper_method, sql_type, sql_fingerprint,
                               sql_text, cost_ms, row_count, created_at
                          FROM admin_db_slow_sql
                        """ + where + " ORDER BY created_at DESC LIMIT ?, ?",
                (rs, rowNum) -> {
                    SlowSqlVO vo = new SlowSqlVO();
                    vo.setId(rs.getLong("id"));
                    vo.setServiceName(rs.getString("service_name"));
                    vo.setMapperMethod(rs.getString("mapper_method"));
                    vo.setSqlType(rs.getString("sql_type"));
                    vo.setSqlFingerprint(rs.getString("sql_fingerprint"));
                    vo.setSqlText(rs.getString("sql_text"));
                    vo.setCostMs(rs.getInt("cost_ms"));
                    vo.setRowCount(rs.getObject("row_count") == null ? null : rs.getInt("row_count"));
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    vo.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
                    return vo;
                }, pageArgs.toArray());

        return PageVO.<SlowSqlVO>builder()
                .list(list)
                .total(totalCount)
                .page(safePage)
                .pageSize(safeSize)
                .build();
    }

    /** 按指纹聚合 Top N */
    public List<SlowSqlTopVO> topSlowSqls(Integer hours, Integer limit) {
        LocalDateTime since = LocalDateTime.now().minusHours(safeHours(hours));
        int safeLimit = Math.min(Math.max(limit == null ? 10 : limit, 1), 100);
        return jdbcTemplate.query("""
                        SELECT sql_fingerprint AS fingerprint, MAX(sql_text) AS sql_text,
                               MAX(sql_type) AS sql_type,
                               GROUP_CONCAT(DISTINCT service_name) AS services,
                               COUNT(*) AS cnt, ROUND(AVG(cost_ms)) AS avg_cost,
                               MAX(cost_ms) AS max_cost, MAX(created_at) AS last_at
                          FROM admin_db_slow_sql
                         WHERE created_at >= ?
                         GROUP BY sql_fingerprint
                         ORDER BY cnt DESC, max_cost DESC
                         LIMIT ?
                        """,
                (rs, rowNum) -> {
                    SlowSqlTopVO vo = new SlowSqlTopVO();
                    vo.setFingerprint(rs.getString("fingerprint"));
                    vo.setSqlText(rs.getString("sql_text"));
                    vo.setSqlType(rs.getString("sql_type"));
                    vo.setServices(rs.getString("services"));
                    vo.setCount(rs.getLong("cnt"));
                    vo.setAvgCostMs(rs.getLong("avg_cost"));
                    vo.setMaxCostMs(rs.getLong("max_cost"));
                    Timestamp lastAt = rs.getTimestamp("last_at");
                    vo.setLastAt(lastAt == null ? null : lastAt.toLocalDateTime());
                    return vo;
                }, since, safeLimit);
    }

    /** 按小时统计条数与耗时 */
    public List<SlowSqlTrendVO> slowSqlTrend(Integer hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(safeHours(hours));
        return jdbcTemplate.query("""
                        SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:00') AS hour_label,
                               COUNT(*) AS cnt, ROUND(AVG(cost_ms)) AS avg_cost,
                               MAX(cost_ms) AS max_cost
                          FROM admin_db_slow_sql
                         WHERE created_at >= ?
                         GROUP BY hour_label
                         ORDER BY hour_label
                        """,
                (rs, rowNum) -> {
                    SlowSqlTrendVO vo = new SlowSqlTrendVO();
                    vo.setHour(rs.getString("hour_label"));
                    vo.setCount(rs.getLong("cnt"));
                    vo.setAvgCostMs(rs.getLong("avg_cost"));
                    vo.setMaxCostMs(rs.getLong("max_cost"));
                    return vo;
                }, since);
    }

    /** 表容量 Top N */
    public List<DbTableSizeVO> tableSizes(Integer limit) {
        int safeLimit = Math.min(Math.max(limit == null ? 10 : limit, 1), 100);
        return jdbcTemplate.query("""
                        SELECT TABLE_NAME AS name, TABLE_ROWS AS row_cnt,
                               DATA_LENGTH AS data_len, INDEX_LENGTH AS index_len,
                               DATA_LENGTH + INDEX_LENGTH AS total_len
                          FROM information_schema.TABLES
                         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
                         ORDER BY total_len DESC
                         LIMIT ?
                        """,
                (rs, rowNum) -> {
                    DbTableSizeVO vo = new DbTableSizeVO();
                    vo.setName(rs.getString("name"));
                    vo.setRows(rs.getLong("row_cnt"));
                    vo.setDataLength(rs.getLong("data_len"));
                    vo.setIndexLength(rs.getLong("index_len"));
                    vo.setTotalLength(rs.getLong("total_len"));
                    vo.setSizeHuman(DatabaseAdminService.humanSize(vo.getTotalLength()));
                    return vo;
                }, safeLimit);
    }

    /** 删除早于 beforeDays 的记录，返回删除行数 */
    public int clear(Integer beforeDays) {
        int days = beforeDays == null || beforeDays < 1 ? 7 : Math.min(beforeDays, 365);
        LocalDateTime before = LocalDateTime.now().minusDays(days);
        return jdbcTemplate.update("DELETE FROM admin_db_slow_sql WHERE created_at < ?", before);
    }

    /** 各服务 Hikari 连接池状态；本服务用 MXBean 覆盖，保证一定有数据 */
    public List<Map<String, Object>> poolStatus() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> item : serviceMetricsService.list()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("service", item.get("name"));
            row.put("active", item.getOrDefault("poolActive", 0L));
            row.put("idle", item.getOrDefault("poolIdle", 0L));
            row.put("pending", item.getOrDefault("poolPending", 0L));
            row.put("max", item.getOrDefault("poolMax", 0L));
            row.put("available", item.getOrDefault("poolAvailable", false));
            list.add(row);
        }
        // 本服务实时值覆盖
        Map<String, Object> own = ownPool();
        if (!own.isEmpty()) {
            for (Map<String, Object> row : list) {
                if ("system-service".equals(row.get("service"))) {
                    row.put("active", own.get("active"));
                    row.put("idle", own.get("idle"));
                    row.put("pending", own.get("pending"));
                    row.put("max", own.get("max"));
                    row.put("available", true);
                }
            }
        }
        return list;
    }

    private Map<String, Object> ownPool() {
        if (!(dataSource instanceof HikariDataSource hikari)) {
            return Map.of();
        }
        HikariPoolMXBean mxBean = hikari.getHikariPoolMXBean();
        if (mxBean == null) {
            return Map.of();
        }
        Map<String, Object> pool = new LinkedHashMap<>();
        pool.put("name", hikari.getPoolName());
        pool.put("active", mxBean.getActiveConnections());
        pool.put("idle", mxBean.getIdleConnections());
        pool.put("pending", mxBean.getThreadsAwaitingConnection());
        pool.put("max", hikari.getMaximumPoolSize());
        return pool;
    }

    /** SHOW 结果转 Map：兼容驱动返回的列名大小写 */
    private Map<String, String> queryPairs(String sql) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql)) {
            String name = null;
            String value = null;
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if ("variable_name".equalsIgnoreCase(entry.getKey())) {
                    name = String.valueOf(entry.getValue());
                } else if ("value".equalsIgnoreCase(entry.getKey())) {
                    value = String.valueOf(entry.getValue());
                }
            }
            if (name != null) {
                result.put(name, value == null ? "" : value);
            }
        }
        return result;
    }

    /** QPS 需要两次采样差值；上次值放 Redis，进程重启也不至于每次都从 0 开始 */
    private double qps(String questionsValue) {
        long questions = parseLong(questionsValue);
        if (questions < 0) {
            return 0;
        }
        long now = System.currentTimeMillis();
        double qps = 0;
        try {
            String last = stringRedisTemplate.opsForValue().get(QPS_KEY);
            if (last != null && last.contains(":")) {
                String[] parts = last.split(":");
                long lastAt = Long.parseLong(parts[0]);
                long lastQuestions = Long.parseLong(parts[1]);
                long seconds = Math.max((now - lastAt) / 1000, 1);
                if (questions >= lastQuestions) {
                    qps = (questions - lastQuestions) / (double) seconds;
                }
            }
            stringRedisTemplate.opsForValue()
                    .set(QPS_KEY, now + ":" + questions, Duration.ofDays(1));
        } catch (RuntimeException e) {
            log.debug("QPS 采样失败（Redis 不可用）: {}", e.getMessage());
        }
        return round(qps);
    }

    private int safeHours(Integer hours) {
        int value = hours == null ? 24 : hours;
        return Math.min(Math.max(value, 1), 24 * 7);
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
