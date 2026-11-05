package ecommerce.security;

import ecommerce.properties.JwtProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台管理员在线会话登记（Redis）。
 * <p>
 * 当前 JWT 是无状态的，token 本身不落 Redis，无法知道「谁在线」。这里在登录时补一层会话登记：
 * key = {@code auth:online:{adminId}:{sid}}，字段记录账号、IP、登录时间、最后活跃等。
 * <p>
 * 会话只用于「在线用户」展示和强制下线，不参与每次请求的鉴权（鉴权仍走 JWT 签名 + 失效时间点）。
 * 所有写操作都 fail-open：Redis 异常只记日志，不能影响登录主流程。
 */
@Slf4j
@Component
public class AdminOnlineSessionStore {

    /** 在线会话 key 前缀：auth:online:{adminId}:{sid} */
    public static final String KEY_PREFIX = "auth:online:";
    /** 活跃时间节流 key 前缀：auth:online:touch:{sid} */
    public static final String TOUCH_PREFIX = "auth:online:touch:";
    /** 同一会话多久刷新一次 lastActive */
    private static final long TOUCH_THROTTLE_SECONDS = 600L;

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtProperties jwtProperties;

    public AdminOnlineSessionStore(StringRedisTemplate stringRedisTemplate, JwtProperties jwtProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jwtProperties = jwtProperties;
    }

    /** 登录成功后登记会话（TTL 与 token 一致） */
    public void register(Long adminId, String sid, String username, String ip,
                         String userAgent, List<String> roles) {
        if (adminId == null || sid == null || sid.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        Map<String, String> data = new LinkedHashMap<>();
        data.put("adminId", String.valueOf(adminId));
        data.put("sid", sid);
        data.put("username", username == null ? "" : username);
        data.put("ip", ip == null ? "" : ip);
        data.put("userAgent", userAgent == null ? "" : userAgent);
        data.put("roles", roles == null ? "" : String.join(",", roles));
        data.put("loginTime", String.valueOf(now));
        data.put("lastActive", String.valueOf(now));
        try {
            String key = KEY_PREFIX + adminId + ":" + sid;
            stringRedisTemplate.opsForHash().putAll(key, data);
            stringRedisTemplate.expire(key, Duration.ofSeconds(sessionTtlSeconds()));
        } catch (RuntimeException e) {
            log.warn("登记在线会话失败, adminId={}: {}", adminId, e.getMessage());
        }
    }

    /**
     * 刷新最后活跃时间。用 SET NX EX 做节流：同一会话 10 分钟内只写一次 Redis。
     */
    public void touch(Long adminId, String sid) {
        if (adminId == null || sid == null || sid.isBlank()) {
            return;
        }
        try {
            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(TOUCH_PREFIX + sid, "1", Duration.ofSeconds(TOUCH_THROTTLE_SECONDS));
            if (!Boolean.TRUE.equals(acquired)) {
                return;
            }
            String key = KEY_PREFIX + adminId + ":" + sid;
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                stringRedisTemplate.opsForHash()
                        .put(key, "lastActive", String.valueOf(System.currentTimeMillis()));
            }
        } catch (RuntimeException e) {
            log.debug("刷新在线会话活跃时间失败: {}", e.getMessage());
        }
    }

    /** 退出登录：删除当前会话 */
    public void remove(Long adminId, String sid) {
        if (adminId == null || sid == null || sid.isBlank()) {
            return;
        }
        try {
            stringRedisTemplate.delete(KEY_PREFIX + adminId + ":" + sid);
            stringRedisTemplate.delete(TOUCH_PREFIX + sid);
        } catch (RuntimeException e) {
            log.warn("删除在线会话失败, adminId={}: {}", adminId, e.getMessage());
        }
    }

    /** 强制下线：删除该账号的全部会话，返回删除数量 */
    public int removeAll(Long adminId) {
        if (adminId == null) {
            return 0;
        }
        List<String> sids = new ArrayList<>();
        List<String> keys = scan(KEY_PREFIX + adminId + ":*");
        for (String key : keys) {
            Object sid = null;
            try {
                sid = stringRedisTemplate.opsForHash().get(key, "sid");
            } catch (RuntimeException e) {
                log.debug("读取会话 sid 失败: {}", e.getMessage());
            }
            if (sid != null) {
                sids.add(String.valueOf(sid));
            }
        }
        List<String> toDelete = new ArrayList<>(keys);
        sids.forEach(sid -> toDelete.add(TOUCH_PREFIX + sid));
        try {
            Long removed = stringRedisTemplate.delete(toDelete);
            return removed == null ? 0 : removed.intValue();
        } catch (RuntimeException e) {
            log.warn("按账号清理在线会话失败, adminId={}: {}", adminId, e.getMessage());
            return 0;
        }
    }

    /** 扫描所有在线会话 key（不含 touch 节流 key） */
    public List<String> scanSessionKeys() {
        List<String> result = new ArrayList<>();
        for (String key : scan(KEY_PREFIX + "*")) {
            // auth:online:{adminId}:{sid} / auth:online:touch:{sid} 都匹配前缀，这里排除 touch
            String suffix = key.substring(KEY_PREFIX.length());
            String[] parts = suffix.split(":");
            if (parts.length == 2 && !"touch".equals(parts[0])) {
                result.add(key);
            }
        }
        return result;
    }

    /** 读取单个会话字段 */
    public Map<String, String> readSession(String key) {
        try {
            Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(key);
            Map<String, String> data = new LinkedHashMap<>();
            raw.forEach((k, v) -> data.put(String.valueOf(k), String.valueOf(v)));
            return data;
        } catch (RuntimeException e) {
            log.debug("读取在线会话失败, key={}: {}", key, e.getMessage());
            return Map.of();
        }
    }

    /** SCAN 分页扫 key，避免 KEYS 阻塞 Redis */
    private List<String> scan(String pattern) {
        try {
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(500).build();
            List<String> keys = stringRedisTemplate.execute(connection -> {
                List<String> result = new ArrayList<>();
                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        result.add(new String(cursor.next(), java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
                return result;
            }, true);
            return keys == null ? List.of() : keys;
        } catch (RuntimeException e) {
            log.warn("扫描 Redis key 失败, pattern={}: {}", pattern, e.getMessage());
            return List.of();
        }
    }

    private long sessionTtlSeconds() {
        long expire = jwtProperties.getExpire();
        return Math.max(60, expire);
    }
}
