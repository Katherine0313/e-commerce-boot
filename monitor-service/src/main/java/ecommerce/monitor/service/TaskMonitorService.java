package ecommerce.monitor.service;

import ecommerce.exception.BusinessException;
import ecommerce.monitor.support.SensitiveTextSanitizer;
import ecommerce.task.MonitoredTaskAspect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时任务：admin_monitor_task 配置 + admin_monitor_task_log 执行记录。
 * <p>
 * 调度执行在各业务服务内（DynamicTaskScheduler），本服务只负责配置读写、日志查询和
 * 手动触发转发（携带管理员令牌调用任务归属服务的 /api/admin/tasks/{name}/run）。
 */
@Slf4j
@Service
public class TaskMonitorService {

    /** 允许手动执行的任务白名单：避免重复推送/批量改单等副作用 */
    private static final List<String> MANUAL_RUN_ALLOWED = List.of("logCleanup", "orphanImageCleanup");

    private static final String SELECT_SQL = """
            SELECT id, task_name, task_group, bean_name, method_name, method_params,
                   task_type, schedule_value, status, concurrent, remark, create_time, update_time
              FROM admin_monitor_task
             %s
             ORDER BY id ASC
            """;

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final DiscoveryClient discoveryClient;
    private final RestClient restClient;

    public TaskMonitorService(JdbcTemplate jdbcTemplate,
                              StringRedisTemplate stringRedisTemplate,
                              DiscoveryClient discoveryClient,
                              RestClient.Builder restClientBuilder) {
        this.jdbcTemplate = jdbcTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.discoveryClient = discoveryClient;
        this.restClient = restClientBuilder.build();
    }

    public List<Map<String, Object>> list(String keyword, Integer status) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (task_name LIKE ? OR task_group LIKE ? OR remark LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (status != null) {
            where.append(" AND status = ?");
            args.add(status);
        }
        List<Map<String, Object>> rows = jdbcTemplate.query(
                String.format(SELECT_SQL, where), (rs, rowNum) -> toItem(rs), args.toArray());
        for (Map<String, Object> item : rows) {
            item.put("lastRun", readLastRun(String.valueOf(item.get("taskName"))));
        }
        return rows;
    }

    public Map<String, Object> create(Map<String, Object> body) {
        TaskForm form = validate(body, null);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO admin_monitor_task
                          (task_name, task_group, bean_name, method_name, method_params,
                           task_type, schedule_value, status, concurrent, remark, create_time, update_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, form.taskName());
                ps.setString(2, form.taskGroup());
                ps.setString(3, form.beanName());
                ps.setString(4, form.methodName());
                ps.setString(5, form.methodParams());
                ps.setInt(6, form.taskType());
                ps.setString(7, form.scheduleValue());
                ps.setInt(8, form.status());
                ps.setInt(9, form.concurrent());
                ps.setString(10, form.remark());
                return ps;
            }, keyHolder);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "任务名称已存在: " + form.taskName());
        }
        Number key = keyHolder.getKey();
        return detail(key == null ? null : key.longValue());
    }

    public Map<String, Object> update(Long id, Map<String, Object> body) {
        TaskForm form = validate(body, id);
        try {
            jdbcTemplate.update("""
                    UPDATE admin_monitor_task
                       SET task_name = ?, task_group = ?, bean_name = ?, method_name = ?,
                           method_params = ?, task_type = ?, schedule_value = ?, status = ?,
                           concurrent = ?, remark = ?, update_time = NOW()
                     WHERE id = ?
                    """, form.taskName(), form.taskGroup(), form.beanName(), form.methodName(),
                    form.methodParams(), form.taskType(), form.scheduleValue(), form.status(),
                    form.concurrent(), form.remark(), id);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "任务名称已存在: " + form.taskName());
        }
        return detail(id);
    }

    public void delete(Long id) {
        int rows = jdbcTemplate.update("DELETE FROM admin_monitor_task WHERE id = ?", id);
        if (rows == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "任务不存在: " + id);
        }
    }

    /** 启停：只改 status，调度器 30 秒内生效 */
    public Map<String, Object> changeStatus(Long id, int status) {
        if (status != 0 && status != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "status 只能是 0 或 1");
        }
        int rows = jdbcTemplate.update(
                "UPDATE admin_monitor_task SET status = ?, update_time = NOW() WHERE id = ?", status, id);
        if (rows == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "任务不存在: " + id);
        }
        return detail(id);
    }

    public List<Map<String, Object>> logs(Long taskId, int limit) {
        int size = Math.max(1, Math.min(200, limit));
        return jdbcTemplate.query("""
                SELECT id, task_id, task_name, trigger_type, status, error_msg, cost_ms,
                       started_at, finished_at, create_time
                  FROM admin_monitor_task_log
                 WHERE task_id = ?
                 ORDER BY id DESC
                 LIMIT ?
                """, (rs, rowNum) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id"));
            item.put("taskId", rs.getLong("task_id"));
            item.put("taskName", rs.getString("task_name"));
            item.put("triggerType", rs.getInt("trigger_type"));
            item.put("status", rs.getInt("status"));
            item.put("errorMsg", SensitiveTextSanitizer.sanitize(rs.getString("error_msg")));
            item.put("costMs", rs.getInt("cost_ms"));
            item.put("startedAt", toLocal(rs.getTimestamp("started_at")));
            item.put("finishedAt", toLocal(rs.getTimestamp("finished_at")));
            item.put("createTime", toLocal(rs.getTimestamp("create_time")));
            return item;
        }, taskId, size);
    }

    /** 手动执行：按任务归属服务转发 */
    public Map<String, Object> run(String name, String authorization) {
        Map<String, Object> task = findByName(name);
        if (!MANUAL_RUN_ALLOWED.contains(name)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "该任务未开放手动执行（避免重复通知/批量改单等风险）");
        }
        String service = String.valueOf(task.get("taskGroup"));
        ServiceInstance instance = resolveInstance(service);
        String url = "http://" + instance.getHost() + ":" + instance.getPort()
                + "/api/admin/tasks/" + name + "/run";
        try {
            restClient.post()
                    .uri(url)
                    .header("Authorization", authorization == null ? "" : authorization)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.warn("手动执行任务失败: name={}, status={}, body={}", name,
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "执行失败: " + extractMessage(e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.warn("手动执行任务异常: name={}, error={}", name, e.getMessage());
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "调用 " + service + " 失败: " + e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("service", service);
        result.put("message", "已触发执行，稍后刷新查看最近执行结果");
        return result;
    }

    /** 日志保留 30 天，每天清理一次（monitor-service 启动时也会跑一次） */
    public void cleanupLogs() {
        try {
            int rows = jdbcTemplate.update(
                    "DELETE FROM admin_monitor_task_log WHERE started_at < ?",
                    Timestamp.valueOf(LocalDateTime.now().minusDays(30)));
            if (rows > 0) {
                log.info("清理定时任务执行日志 {} 条", rows);
            }
        } catch (RuntimeException e) {
            log.warn("清理定时任务执行日志失败: {}", e.getMessage());
        }
    }

    private Map<String, Object> detail(Long id) {
        if (id == null) {
            return Map.of();
        }
        Map<String, Object> item = jdbcTemplate.query(
                String.format(SELECT_SQL, " WHERE id = ?"), rs -> rs.next() ? toItem(rs) : null, id);
        if (item != null) {
            item.put("lastRun", readLastRun(String.valueOf(item.get("taskName"))));
        }
        return item == null ? Map.of() : item;
    }

    private Map<String, Object> findByName(String name) {
        Map<String, Object> item = jdbcTemplate.query(
                String.format(SELECT_SQL, " WHERE task_name = ?"),
                rs -> rs.next() ? toItem(rs) : null, name);
        if (item == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "任务不存在: " + name);
        }
        return item;
    }

    private Map<String, Object> toItem(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", rs.getLong("id"));
        item.put("taskName", rs.getString("task_name"));
        item.put("name", rs.getString("task_name"));
        item.put("taskGroup", rs.getString("task_group"));
        item.put("service", rs.getString("task_group"));
        item.put("beanName", rs.getString("bean_name"));
        item.put("methodName", rs.getString("method_name"));
        item.put("methodParams", rs.getString("method_params"));
        item.put("taskType", rs.getInt("task_type"));
        item.put("scheduleValue", rs.getString("schedule_value"));
        item.put("cron", describeSchedule(rs.getInt("task_type"), rs.getString("schedule_value")));
        item.put("status", rs.getInt("status"));
        item.put("concurrent", rs.getInt("concurrent"));
        item.put("remark", rs.getString("remark"));
        item.put("description", rs.getString("remark"));
        item.put("allowManualRun", MANUAL_RUN_ALLOWED.contains(rs.getString("task_name"))
                && rs.getInt("status") == 1);
        item.put("createTime", toLocal(rs.getTimestamp("create_time")));
        item.put("updateTime", toLocal(rs.getTimestamp("update_time")));
        return item;
    }

    private String describeSchedule(int taskType, String value) {
        if (taskType == 1) {
            return value;
        }
        return "每 " + value + " 毫秒";
    }

    private TaskForm validate(Map<String, Object> body, Long id) {
        if (body == null || body.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "参数不能为空");
        }
        String taskName = trim(body.get("taskName"));
        if (taskName.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "任务名称不能为空");
        }
        int taskType = intValue(body.get("taskType"), 1);
        if (taskType < 1 || taskType > 3) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "taskType 只能是 1/2/3");
        }
        String scheduleValue = trim(body.get("scheduleValue"));
        if (scheduleValue.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "执行周期不能为空");
        }
        validateSchedule(taskType, scheduleValue);
        String taskGroup = trim(body.get("taskGroup"));
        if (taskGroup.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "归属服务不能为空");
        }
        Long existing = jdbcTemplate.query(
                "SELECT id FROM admin_monitor_task WHERE task_name = ?",
                rs -> rs.next() ? rs.getLong(1) : null, taskName);
        if (existing != null && (id == null || !existing.equals(id))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "任务名称已存在: " + taskName);
        }
        int status = intValue(body.get("status"), 1);
        int concurrent = intValue(body.get("concurrent"), 1);
        String beanName = trim(body.get("beanName"));
        String methodName = trim(body.get("methodName"));
        return new TaskForm(taskName, taskGroup,
                beanName.isBlank() ? taskName : beanName,
                methodName.isBlank() ? taskName : methodName,
                trim(body.get("methodParams")), taskType, scheduleValue,
                status == 0 ? 0 : 1, concurrent == 0 ? 0 : 1, trim(body.get("remark")));
    }

    private void validateSchedule(int taskType, String value) {
        if (taskType == 1) {
            try {
                CronExpression.parse(value);
            } catch (RuntimeException e) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "cron 表达式非法: " + value);
            }
            return;
        }
        try {
            long millis = Long.parseLong(value);
            if (millis < 1000) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "间隔不能小于 1000 毫秒");
            }
        } catch (NumberFormatException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "间隔必须是毫秒数: " + value);
        }
    }

    private ServiceInstance resolveInstance(String serviceName) {
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        if (instances == null || instances.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Nacos 未找到服务实例: " + serviceName);
        }
        return instances.get(0);
    }

    /** 最近一次执行记录（切面写入的 Hash） */
    private Map<String, Object> readLastRun(String name) {
        try {
            Map<Object, Object> raw = stringRedisTemplate.opsForHash()
                    .entries(MonitoredTaskAspect.LAST_KEY_PREFIX + name);
            if (raw == null || raw.isEmpty()) {
                return null;
            }
            Map<String, Object> lastRun = new LinkedHashMap<>();
            lastRun.put("startTime", toTime(raw.get("startTime")));
            lastRun.put("endTime", toTime(raw.get("endTime")));
            lastRun.put("costMs", toLong(raw.get("costMs")));
            lastRun.put("success", Boolean.parseBoolean(String.valueOf(raw.get("success"))));
            lastRun.put("message", SensitiveTextSanitizer.sanitize(
                    raw.getOrDefault("message", "").toString()));
            return lastRun;
        } catch (RuntimeException e) {
            log.debug("读取任务执行记录失败, name={}: {}", name, e.getMessage());
            return null;
        }
    }

    private LocalDateTime toTime(Object millis) {
        long value = toLong(millis);
        if (value <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneId.systemDefault());
    }

    private LocalDateTime toLocal(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 从下游 Result JSON 里取 message，取不到就用原文 */
    private String extractMessage(String body) {
        if (body == null || body.isBlank()) {
            return "服务返回空响应";
        }
        int index = body.indexOf("\"message\"");
        if (index < 0) {
            return body.length() > 200 ? body.substring(0, 200) : body;
        }
        int start = body.indexOf('"', index + 9);
        int end = start < 0 ? -1 : body.indexOf('"', start + 1);
        if (start < 0 || end < 0) {
            return body;
        }
        return body.substring(start + 1, end);
    }

    private String trim(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private record TaskForm(String taskName, String taskGroup, String beanName, String methodName,
                            String methodParams, int taskType, String scheduleValue,
                            int status, int concurrent, String remark) {
    }
}
