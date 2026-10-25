package ecommerce.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信 access_token 统一提供者。
 * <p>
 * 多个服务（user-service 的手机号、order-service 的订阅消息）都要调微信接口，
 * 如果各自缓存 token，会把对方的 token 刷失效（微信重复获取会让旧 token 失效）。
 * 所以统一放 Redis 共享：
 * <ul>
 *   <li>key = wechat:access_token:{appid}，TTL 比 expires_in 少 5 分钟</li>
 *   <li>刷新时用 setIfAbsent 抢 10 秒锁，避免并发重复请求</li>
 *   <li>Redis 不可用时退回进程内 volatile 缓存，不影响主流程</li>
 * </ul>
 */
@Slf4j
@Component
public class WeChatAccessTokenProvider {

    private static final String TOKEN_KEY = "wechat:access_token:%s";
    private static final String LOCK_KEY = "wechat:access_token:lock:%s";
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);

    @Value("${wechat.appid:}")
    private String appid;
    @Value("${wechat.secret:}")
    private String secret;

    private final StringRedisTemplate stringRedisTemplate;
    private final HttpClientUtil httpClientUtil;
    private final ObjectMapper objectMapper;

    /** Redis 不可用时的进程内兜底缓存 */
    private volatile String fallbackToken;
    private volatile long fallbackExpireAt;

    public WeChatAccessTokenProvider(StringRedisTemplate stringRedisTemplate,
                                     HttpClientUtil httpClientUtil,
                                     ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.httpClientUtil = httpClientUtil;
        this.objectMapper = objectMapper;
    }

    /** 获取 access_token：Redis 命中直接返回，未命中则刷新 */
    public String getAccessToken() {
        if (appid == null || appid.isBlank() || secret == null || secret.isBlank()) {
            throw new IllegalStateException("wechat.appid / wechat.secret 未配置，无法获取 access_token");
        }
        String key = TOKEN_KEY.formatted(appid);

        // 1. 优先读 Redis
        String cached = safeGet(key);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }

        // 2. 抢刷新锁：抢不到说明别的实例正在刷新，稍等后重读缓存
        String lockKey = LOCK_KEY.formatted(appid);
        boolean locked = safeSetIfAbsent(lockKey, "1", LOCK_TTL);
        if (!locked) {
            for (int i = 0; i < 5; i++) {
                sleepQuietly(200);
                String waiting = safeGet(key);
                if (waiting != null && !waiting.isBlank()) {
                    return waiting;
                }
            }
            // 等不到就直接刷新（宁可多取一次，也不能让业务卡住）
        }

        try {
            return refresh(key);
        } finally {
            if (locked) {
                safeDelete(lockKey);
            }
        }
    }

    /** token 失效时调用：只删除与传入 token 相同的那份，避免误删刚刷新的新 token */
    public void invalidate(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        String key = TOKEN_KEY.formatted(appid);
        String cached = safeGet(key);
        if (token.equals(cached)) {
            safeDelete(key);
        }
        if (token.equals(fallbackToken)) {
            fallbackToken = null;
            fallbackExpireAt = 0;
        }
    }

    /** 调微信接口刷新 token，并写入 Redis + 内存兜底 */
    private String refresh(String key) {
        Map<String, String> params = new HashMap<>();
        params.put("grant_type", "client_credential");
        params.put("appid", appid);
        params.put("secret", secret);

        String respBody = httpClientUtil.doGet("https://api.weixin.qq.com/cgi-bin/token", params);
        JsonNode node = objectMapper.readTree(respBody);
        String token = node.path("access_token").asText(null);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("获取微信 access_token 失败：" + respBody);
        }
        long expiresIn = node.path("expires_in").asLong(7200);
        // 提前 5 分钟过期，避免临界点拿到失效 token
        Duration ttl = Duration.ofSeconds(Math.max(60, expiresIn - 300));

        safeSet(key, token, ttl);
        fallbackToken = token;
        fallbackExpireAt = System.currentTimeMillis() + ttl.toMillis();
        log.info("微信 access_token 已刷新，appid={}，ttl={}s", appid, ttl.toSeconds());
        return token;
    }

    // ==================== Redis 降级包装：Redis 挂了用内存兜底 ====================

    private String safeGet(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("access_token 读取 Redis 失败，改用内存缓存", e);
            return fallbackValid() ? fallbackToken : null;
        }
    }

    private void safeSet(String key, String value, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("access_token 写入 Redis 失败，仅保留内存缓存", e);
        }
    }

    private boolean safeSetIfAbsent(String key, String value, Duration ttl) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, value, ttl));
        } catch (Exception e) {
            // Redis 不可用：直接放行，让当前请求去刷新，避免业务卡死
            return true;
        }
    }

    private void safeDelete(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("access_token 删除 Redis key 失败: {}", key, e);
        }
    }

    private boolean fallbackValid() {
        return fallbackToken != null && System.currentTimeMillis() < fallbackExpireAt;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
