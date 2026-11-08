package ecommerce.system.service;

import ecommerce.exception.BusinessException;
import ecommerce.security.AdminTokenRevocationStore;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.async.RedisAsyncCommands;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 缓存监控：Redis INFO 概览 + SCAN 游标分页 + 单 key 删除。
 * <p>
 * 安全约束：
 * 1. 禁止删除 auth:admin:invalid-before:*，否则已被强制下线的旧 token 会重新可用；
 * 2. 列表只回传脱敏 key（保留前缀、掩码随机值），完整 key 需单独权限再显式查看，
 *    删除也按列表返回的临时 token 定位，页面不再直接持有完整 key。
 */
@Slf4j
@Service
public class CacheMonitorService {

    /** 单次返回的最大 key 数 */
    private static final int MAX_PAGE_SIZE = 200;
    /** 一次请求最多进行多少轮 SCAN，避免大库拖慢接口 */
    private static final int MAX_SCAN_CYCLES = 5;
    /** 单轮 SCAN 建议扫描的槽位数 */
    private static final int SCAN_COUNT = 1000;
    /** 完整 key 的临时引用：token -> 完整 key，避免列表直接外传 */
    private static final String KEY_REF_PREFIX = "monitor:cache:keyref:";
    /** 引用有效期：列表数据本来就要求重新扫描，10 分钟足够操作 */
    private static final Duration KEY_REF_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate stringRedisTemplate;

    public CacheMonitorService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Map<String, Object> overview() {
        Properties server = info("server");
        Properties clients = info("clients");
        Properties memory = info("memory");
        Properties stats = info("stats");
        Properties keyspace = info("keyspace");

        long hits = longValue(stats, "keyspace_hits");
        long misses = longValue(stats, "keyspace_misses");
        double hitRate = (hits + misses) > 0 ? round((double) hits * 100 / (hits + misses)) : 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", property(server, "redis_version"));
        result.put("mode", property(server, "redis_mode"));
        result.put("uptimeSeconds", longValue(server, "uptime_in_seconds"));
        result.put("connectedClients", longValue(clients, "connected_clients"));
        result.put("blockedClients", longValue(clients, "blocked_clients"));
        result.put("usedMemory", longValue(memory, "used_memory"));
        result.put("usedMemoryHuman", property(memory, "used_memory_human"));
        result.put("usedMemoryPeakHuman", property(memory, "used_memory_peak_human"));
        result.put("maxMemory", longValue(memory, "maxmemory"));
        result.put("keyspaceHits", hits);
        result.put("keyspaceMisses", misses);
        result.put("hitRate", hitRate);
        result.put("expiredKeys", longValue(stats, "expired_keys"));
        result.put("evictedKeys", longValue(stats, "evicted_keys"));
        result.put("opsPerSec", longValue(stats, "instantaneous_ops_per_sec"));
        result.put("dbs", dbs(keyspace));
        return result;
    }

    /**
     * SCAN 分页查询 key。
     *
     * @param prefix   前缀过滤（如 auth:），为空表示全部
     * @param keyword  包含匹配关键字，为空表示不过滤
     * @param cursor   SCAN 游标，首次传 0
     * @param pageSize 单页数量，最大 200
     */
    public Map<String, Object> keys(String prefix, String keyword, long cursor, int pageSize) {
        int size = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
        String match = (prefix == null || prefix.isBlank() ? "" : prefix.trim()) + "*";

        List<String> keys = new ArrayList<>();
        long current = Math.max(0, cursor);
        long nextCursor = 0;
        int cycles = 0;
        boolean finished = false;
        String error = null;
        do {
            ScanPage page = scanOnce(current, match);
            if (page.error() != null) {
                error = page.error();
            }
            nextCursor = page.cursor();
            for (String key : page.keys()) {
                if (keyword != null && !keyword.isBlank() && !key.contains(keyword.trim())) {
                    continue;
                }
                keys.add(key);
                if (keys.size() >= size) {
                    return pageResult(keys, nextCursor, false, null);
                }
            }
            current = nextCursor;
            cycles++;
            if (current == 0 || error != null) {
                finished = true;
            }
        } while (!finished && cycles < MAX_SCAN_CYCLES);

        return pageResult(keys, 0, finished, error);
    }

    /** 查看完整 key：只认列表下发的临时 token，避免随便猜 key */
    public String reveal(String token) {
        String key = resolveKey(token);
        if (key == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "记录已过期，请重新扫描后再查看");
        }
        return key;
    }

    /** 删除单个 key（按列表下发的 token 定位）；危险 key 直接拒绝 */
    public boolean deleteByToken(String token) {
        String key = resolveKey(token);
        if (key == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "记录已过期，请重新扫描后再删除");
        }
        if (key.startsWith(AdminTokenRevocationStore.KEY_PREFIX)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "禁止删除管理员令牌失效标记，否则已强制下线的旧 token 会重新可用");
        }
        try {
            Boolean deleted = stringRedisTemplate.delete(key);
            return Boolean.TRUE.equals(deleted);
        } catch (RuntimeException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "删除缓存失败: " + e.getMessage());
        }
    }

    /** token -> 完整 key，找不到（过期/伪造）返回 null */
    private String resolveKey(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return stringRedisTemplate.opsForValue().get(KEY_REF_PREFIX + token.trim());
        } catch (RuntimeException e) {
            log.warn("读取缓存 key 引用失败: {}", e.getMessage());
            return null;
        }
    }

    /** 为本次扫描结果生成临时引用，页面用 token 查看/删除，不再持有完整 key */
    private String rememberKey(String key) {
        String token = UUID.randomUUID().toString().replace("-", "");
        try {
            stringRedisTemplate.opsForValue().set(KEY_REF_PREFIX + token, key, KEY_REF_TTL);
        } catch (RuntimeException e) {
            log.warn("写入缓存 key 引用失败: {}", e.getMessage());
        }
        return token;
    }

    /** 展示用脱敏：保留最后一个冒号之前的前缀，后面的随机值掩码 */
    private String maskKey(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        int index = key.lastIndexOf(':');
        return index >= 0 ? key.substring(0, index + 1) + "******" : "******";
    }

    private Map<String, Object> pageResult(List<String> keys, long cursor, boolean finished, String error) {
        List<Map<String, Object>> items = new ArrayList<>(keys.size());
        for (String key : keys) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("token", rememberKey(key));
            item.put("key", maskKey(key));
            try {
                item.put("type", stringRedisTemplate.type(key).code());
                Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
                // -1 永久 / -2 不存在
                item.put("ttl", ttl == null ? -2 : ttl);
            } catch (RuntimeException e) {
                item.put("type", "unknown");
                item.put("ttl", -2);
            }
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cursor", cursor);
        result.put("finished", finished);
        result.put("list", items);
        if (error != null) {
            result.put("error", error);
        }
        return result;
    }

    /**
     * 执行一次 SCAN。RedisConnection#execute 对多值命令只返回第一个元素（游标），
     * 因此这里直接用 Lettuce 原生游标 API 读取 cursor + keys。
     */
    private ScanPage scanOnce(long cursor, String match) {
        try {
            return stringRedisTemplate.execute((RedisCallback<ScanPage>) connection -> {
                Object nativeConnection = connection.getNativeConnection();
                if (!(nativeConnection instanceof RedisAsyncCommands<?, ?> asyncCommands)) {
                    return new ScanPage(0, List.of(),
                            "不支持的 Redis 连接类型: "
                                    + (nativeConnection == null ? "null" : nativeConnection.getClass().getName()));
                }
                @SuppressWarnings("unchecked")
                RedisAsyncCommands<byte[], byte[]> commands =
                        (RedisAsyncCommands<byte[], byte[]>) asyncCommands;
                ScanCursor scanCursor = cursor > 0 ? ScanCursor.of(String.valueOf(cursor)) : ScanCursor.INITIAL;
                KeyScanCursor<byte[]> page;
                try {
                    page = commands.scan(scanCursor, ScanArgs.Builder.matches(match).limit(SCAN_COUNT))
                            .get(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new ScanPage(0, List.of(), "SCAN 被中断");
                } catch (ExecutionException | TimeoutException e) {
                    return new ScanPage(0, List.of(), "SCAN 失败: " + e.getMessage());
                }
                List<String> keys = new ArrayList<>(page.getKeys().size());
                for (byte[] key : page.getKeys()) {
                    keys.add(new String(key, StandardCharsets.UTF_8));
                }
                long next = 0;
                try {
                    next = Long.parseLong(page.getCursor());
                } catch (NumberFormatException ignored) {
                    // 游标异常时按结束处理，避免死循环
                }
                return new ScanPage(next, keys, null);
            });
        } catch (RuntimeException e) {
            log.warn("SCAN 缓存 key 失败: {}", e.getMessage());
            return new ScanPage(0, List.of(), "SCAN 失败: " + e.getMessage());
        }
    }

    private Properties info(String section) {
        try {
            Properties properties = stringRedisTemplate.execute(
                    (RedisCallback<Properties>) connection -> connection.info(section));
            return properties == null ? new Properties() : properties;
        } catch (RuntimeException e) {
            log.warn("读取 Redis INFO {} 失败: {}", section, e.getMessage());
            return new Properties();
        }
    }

    private List<Map<String, Object>> dbs(Properties keyspace) {
        List<Map<String, Object>> dbs = new ArrayList<>();
        for (String name : keyspace.stringPropertyNames()) {
            if (!name.startsWith("db")) {
                continue;
            }
            Map<String, Object> db = new LinkedHashMap<>();
            db.put("name", name);
            db.put("detail", keyspace.getProperty(name));
            String detail = keyspace.getProperty(name);
            db.put("keys", extractLong(detail, "keys="));
            db.put("expires", extractLong(detail, "expires="));
            dbs.add(db);
        }
        return dbs;
    }

    private long extractLong(String text, String field) {
        if (text == null) {
            return 0;
        }
        int index = text.indexOf(field);
        if (index < 0) {
            return 0;
        }
        int start = index + field.length();
        int end = start;
        while (end < text.length() && Character.isDigit(text.charAt(end))) {
            end++;
        }
        try {
            return Long.parseLong(text.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String property(Properties properties, String key) {
        return properties.getProperty(key, "");
    }

    private long longValue(Properties properties, String key) {
        try {
            return Long.parseLong(properties.getProperty(key, "0").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record ScanPage(long cursor, List<String> keys, String error) {
    }
}
