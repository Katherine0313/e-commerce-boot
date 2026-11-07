package ecommerce.user.support;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PC 短信验证码存取。
 * <p>
 * 登录与绑定手机号共用同一份验证码，统一放这里，避免两处各写一遍 Lua 脚本。
 */
@Component
public class PhoneCodeStore {

    /** 验证码 key：pc:auth:code:{phone} */
    public static final String CODE_KEY_PREFIX = "pc:auth:code:";

    /**
     * 取出并删除验证码（一次性使用）。
     * <p>
     * 不用 opsForValue().getAndDelete()：那是 Redis 6.2+ 的 GETDEL 命令，
     * 旧版 Redis 不支持会报 unknown command。Lua 脚本同样原子，且兼容所有版本。
     */
    private static final DefaultRedisScript<String> GET_AND_DELETE = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]); if v then redis.call('DEL', KEYS[1]) end; return v",
            String.class);

    private final StringRedisTemplate stringRedisTemplate;

    public PhoneCodeStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public static String codeKey(String phone) {
        return CODE_KEY_PREFIX + phone;
    }

    /** 取出并删除验证码；Redis 异常直接抛给调用方，由调用方决定提示文案 */
    public String take(String phone) {
        return stringRedisTemplate.execute(GET_AND_DELETE, List.of(codeKey(phone)));
    }
}
