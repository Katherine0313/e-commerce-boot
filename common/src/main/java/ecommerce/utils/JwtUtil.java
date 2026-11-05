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
import java.util.List;
import java.util.Map;

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
     * 对外暴露生成的方法，给调用方（WxLoginController）调用
     */
    public String generateUserToken(Long userId){
        return buildToken(userId, TYPE_USER, new HashMap<>());
    }
    public String generateAdminToken(Long adminId, String username,
                                     List<String> roles, List<String> permissions,
                                     String sid){
        // 创建哈希表
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("id", adminId);
        extraClaims.put("username", username);
        extraClaims.put("roles", roles == null ? List.of() : roles);
        extraClaims.put("permissions", permissions == null ? List.of() : permissions);
        // sid：会话标识，用于在线用户列表与退出登录时精确删除会话；老 token 没有该 claim 仍可正常鉴权
        if (sid != null && !sid.isBlank()) {
            extraClaims.put("sid", sid);
        }
        return buildToken(adminId, TYPE_ADMIN, extraClaims);

    }
    private String buildToken(Long id, String type, Map<String, Object> extraClaims) {
        return buildToken(id, type, extraClaims, jwtProperties.getExpire());
    }

    /**
     * 校验签名与有效期并解析出载荷；签名不符 / 已过期 / 格式非法都会抛 JwtException
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 真正组装、签名、生成 JWT 字符串的核心方法
     */
    private String buildToken(Long id, String type, Map<String, Object> extraClaims,
                              long expireSeconds) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(id))
                .claim("type",type)
                .claims(extraClaims)
                .issuedAt(new Date(now))
                // expire时间换算，秒换成毫秒
                .expiration(new Date(now + expireSeconds * 1000))
                // 不指定算法，jjwt按密钥长度自动选
                .signWith(secretKey)
                .compact();
    }
}
