package ecommerce.order.ws;

import io.jsonwebtoken.Claims;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手鉴权拦截器。
 * <p>
 * 浏览器 new WebSocket 不能带自定义 header，token 走 query 参数 `?token=xxx`；
 * 握手阶段解析并校验 admin 令牌。校验失败返回 false 拒绝握手，前端收到连接错误后按需重新登录。
 */
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    public AuthHandshakeInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request.getURI().getQuery());
        if (token == null) {
            return false;
        }
        try {
            Claims claims = jwtUtil.parseToken(token);
            // 只允许后台管理员连接
            return JwtUtil.TYPE_ADMIN.equals(claims.get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    /** 从 query string 里取 token 参数（形如 token=xxx 或 &token=xxx） */
    private String extractToken(String query) {
        if (query == null) {
            return null;
        }
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "token".equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }
}
