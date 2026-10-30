package ecommerce.user.support;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * PC 端密码工具：注册统一写 BCrypt 密文。
 * 与后台 admin 的 PasswordUtil 分开：这里不做历史明文兼容。
 */
public final class PasswordUtil {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordUtil() {
    }

    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    public static boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        return ENCODER.matches(rawPassword, storedPassword);
    }
}
