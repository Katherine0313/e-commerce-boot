package ecommerce.system.support;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 后台密码工具：新密码统一 BCrypt，兼容历史明文密码。
 * <p>
 * 历史 admin 表存的是明文；首次登录成功后会自动升级为 BCrypt。
 */
public final class PasswordUtil {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordUtil() {
    }

    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /** 是否是 BCrypt 密文 */
    public static boolean isEncoded(String password) {
        return password != null && password.startsWith("$2");
    }

    /** 兼容校验：BCrypt 走 matches，历史明文直接比较 */
    public static boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        return isEncoded(storedPassword)
                ? ENCODER.matches(rawPassword, storedPassword)
                : storedPassword.equals(rawPassword);
    }
}
