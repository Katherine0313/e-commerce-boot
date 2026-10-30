package ecommerce.security;

import ecommerce.properties.JwtProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 管理员令牌失效登记（Redis）。
 * <p>
 * 后台权限写在 JWT 里，角色/菜单/账号变更后旧 token 在有效期内仍然可用。
 * 这里给每个管理员记录一个「失效时间点」：签发时间早于该时间点的 admin token 一律拒绝。
 * <p>
 * 只允许 fail-open：Redis 异常时放行并限流打日志，避免缓存故障导致后台完全无法登录。
 */
@Slf4j
@Component
public class AdminTokenRevocationStore {

    /** 失效时间点 key：auth:admin:invalid-before:{adminId}，值为 epoch 秒 */
    public static final String KEY_PREFIX = "auth:admin:invalid-before:";

    /** Redis 异常日志的最小间隔（毫秒），避免每请求刷日志 */
    private static final long WARN_INTERVAL_MS = 60_000L;

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtProperties jwtProperties;
    private final AtomicLong lastWarnAt = new AtomicLong(0);

    public AdminTokenRevocationStore(StringRedisTemplate stringRedisTemplate, JwtProperties jwtProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jwtProperties = jwtProperties;
    }

    /** 让该管理员已签发的 token 立即失效（不存在的 id 忽略） */
    public void invalidate(Long adminId) {
        if (adminId == null) {
            return;
        }
        // 失效标记至少要保留一个 token 生命周期，之后旧 token 也自然过期了
        long ttlSeconds = Math.max(60, jwtProperties.getExpire() + 60);
        try {
            stringRedisTemplate.opsForValue().set(KEY_PREFIX + adminId,
                    String.valueOf(Instant.now().getEpochSecond()), Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException e) {
            warnThrottled("写入管理员令牌失效标记失败, adminId=" + adminId + ": " + e.getMessage());
        }
    }

    /** 批量失效（去空、去重） */
    public void invalidateAll(Collection<Long> adminIds) {
        if (adminIds == null || adminIds.isEmpty()) {
            return;
        }
        adminIds.stream().filter(Objects::nonNull).distinct().forEach(this::invalidate);
    }

    /**
     * 判断某个 admin token 是否已失效。
     *
     * @param adminId              管理员 ID
     * @param issuedAtEpochSeconds token 的 iat（epoch 秒）
     * @return true=已失效；Redis 异常或数据异常时返回 false（放行）
     */
    public boolean isInvalid(Long adminId, long issuedAtEpochSeconds) {
        if (adminId == null) {
            return false;
        }
        try {
            String value = stringRedisTemplate.opsForValue().get(KEY_PREFIX + adminId);
            if (value == null || value.isBlank()) {
                return false;
            }
            long invalidBefore = Long.parseLong(value.trim());
            // 严格小于：同一秒内新签发的 token 仍然有效，最多 1 秒窗口
            return issuedAtEpochSeconds < invalidBefore;
        } catch (RuntimeException e) {
            warnThrottled("读取管理员令牌失效标记失败, adminId=" + adminId + ": " + e.getMessage());
            return false;
        }
    }

    /** 日志限流：每分钟最多一条，避免 Redis 故障时刷爆日志 */
    private void warnThrottled(String message) {
        long now = System.currentTimeMillis();
        long last = lastWarnAt.get();
        if (now - last >= WARN_INTERVAL_MS && lastWarnAt.compareAndSet(last, now)) {
            log.warn(message);
        }
    }
}
