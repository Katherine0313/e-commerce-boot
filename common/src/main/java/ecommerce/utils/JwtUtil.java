package ecommerce.utils;

import ecommerce.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类：签发与解析令牌
 */
@Component
public class JwtUtil {

    /**
     * 身份类型：用户/顾客（小程序 / PC 端共用）
     */
    public static final String TYPE_USER = "user";
    /**
     * 身份类型：后台管理员（预留，后台管理系统接入后使用）
     */
    public static final String TYPE_ADMIN = "admin";

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        // 启动时就把密钥建好；长度不足 32 字节 jjwt 会直接抛 WeakKeyException，避免带病启动
        this.secretKey = Keys.hmacShaKeyFor(
                jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
    /**
     ** 对外暴露生成的方法，给调用方（WxLoginController）调用
     */
    public String generateUserToken(Long userId, Long shopId){
        Map<String, Object> extraClaims = new HashMap<>();
        // C 端用户也签 shopId（多租户隔离键），下游 JwtInterceptor 统一解析写入 UserContext
        extraClaims.put("shopId", shopId);
        return buildToken(userId, TYPE_USER, extraClaims);
    }

    /**
     * 签发后台管理员令牌。
     * <p>
     * 前端 getCurrentUser() 直接解 token 的 payload 读 username、role 做权限判断（isAdmin 查 role==='admin'），
     * 所以这几个字段必须写进 payload，不能只塞 subject。shopId 是多租户隔离键，也要一并签进去。
     */
    public String generateAdminToken(Long adminId, String username, String role, Long shopId) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("id", adminId);
        extraClaims.put("username", username);
        extraClaims.put("role", role);
        extraClaims.put("shopId", shopId);
        return buildToken(adminId, TYPE_ADMIN, extraClaims);
    }

    /**
     **真正组装、签名、生成 JWT 字符串的核心方法**。
     */
    private String buildToken(Long id, String type, Map<String, Object>
                              extraClaims){
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(id))
                .claim("type",type)
                .claims(extraClaims)
                .issuedAt(new Date(now))
                // expire时间换算，秒换成毫秒
                .expiration(new Date(now + jwtProperties.getExpire() * 1000))
                // 不指定算法，jjwt按密钥长度自动选
                .signWith(secretKey)
                .compact();

    }

    /**
     * 解析并校验令牌（签名不对、已过期、格式错误都会抛 JwtException）
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
