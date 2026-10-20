package ecommerce.order.config;

import ecommerce.order.ws.AuthHandshakeInterceptor;
import ecommerce.order.ws.OrderWebSocketHandler;
import ecommerce.utils.JwtUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置：注册 /ws/order 端点 + 握手鉴权拦截器。
 * <p>
 * 前端连接地址 ws://网关/ws/order?token=xxx（网关把 /ws/** 转发到 order-service）。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final JwtUtil jwtUtil;
    private final OrderWebSocketHandler orderWebSocketHandler;

    public WebSocketConfig(JwtUtil jwtUtil, OrderWebSocketHandler orderWebSocketHandler) {
        this.jwtUtil = jwtUtil;
        this.orderWebSocketHandler = orderWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(orderWebSocketHandler, "/ws/order")
                .addInterceptors(new AuthHandshakeInterceptor(jwtUtil))
                .setAllowedOrigins("*");
    }
}
