package ecommerce.task;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 定时任务执行记录切面：把最近一次执行结果写到 Redis（保留 7 天）。
 * <p>
 * 只做记录，不改变任务本身的行为；Redis 异常一律吞掉，不能影响业务任务。
 */
@Slf4j
@Aspect
@Component
public class MonitoredTaskAspect {

    /** 最近执行记录 key 前缀：monitor:task:last:{name} */
    public static final String LAST_KEY_PREFIX = "monitor:task:last:";
    private static final Duration RECORD_TTL = Duration.ofDays(7);
    private static final int MAX_MESSAGE_LENGTH = 300;

    private final StringRedisTemplate stringRedisTemplate;

    public MonitoredTaskAspect(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Around("@annotation(task)")
    public Object record(ProceedingJoinPoint joinPoint, MonitoredTask task) throws Throwable {
        long start = System.currentTimeMillis();
        boolean success = true;
        String message = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            success = false;
            message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw e;
        } finally {
            writeRecord(task.name(), start, success, message);
        }
    }

    private void writeRecord(String name, long start, boolean success, String message) {
        if (name == null || name.isBlank()) {
            return;
        }
        try {
            long end = System.currentTimeMillis();
            Map<String, String> data = new LinkedHashMap<>();
            data.put("name", name);
            data.put("startTime", String.valueOf(start));
            data.put("endTime", String.valueOf(end));
            data.put("costMs", String.valueOf(end - start));
            data.put("success", String.valueOf(success));
            if (message != null) {
                String trimmed = message.length() > MAX_MESSAGE_LENGTH
                        ? message.substring(0, MAX_MESSAGE_LENGTH) : message;
                data.put("message", trimmed);
            }
            String key = LAST_KEY_PREFIX + name;
            stringRedisTemplate.opsForHash().putAll(key, data);
            stringRedisTemplate.expire(key, RECORD_TTL);
        } catch (RuntimeException e) {
            log.debug("写入定时任务执行记录失败, name={}: {}", name, e.getMessage());
        }
    }
}
