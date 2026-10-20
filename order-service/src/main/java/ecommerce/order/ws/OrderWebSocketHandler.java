package ecommerce.order.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 订单 WebSocket 处理器：只管连接的建立与关闭，注册/注销到会话注册表。
 * <p>
 * 本服务是服务端单向推送（新订单提醒），不处理客户端发来的消息，故不重写 handleTextMessage。
 */
@Component
public class OrderWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionRegistry sessionRegistry;

    public OrderWebSocketHandler(WebSocketSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long shopId = (Long) session.getAttributes().get("shopId");
        sessionRegistry.add(shopId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long shopId = (Long) session.getAttributes().get("shopId");
        sessionRegistry.remove(shopId, session);
    }
}
