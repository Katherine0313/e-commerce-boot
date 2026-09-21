package ecommerce.properties;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置，对应 application.yml 中的 jwt.*
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    /** HMAC 签名密钥，至少 32 字节；长度决定算法：32→HS256，48→HS384，64→HS512 */
    private String secret;
    /** token 有效期，单位：秒 */
    private long expire;

    /**
     * 启动时校验：密钥没配就直接失败，避免登录时才报错
     */
    @PostConstruct
    public void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret 未配置，请在 application-dev.yml 或环境变量 JWT_SECRET 中设置");
        }
    }
}
