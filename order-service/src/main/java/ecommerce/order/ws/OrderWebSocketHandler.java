package ecommerce.order.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 订单 WebSocket 处理器：负责连接的建立/关闭与心跳应答。
 * <p>
 * 业务推送是服务端 → 客户端单向；客户端只发心跳 ping，服务端回 pong 保持连接活跃。
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

    /** 心跳：前端每 25 秒发一次 {"type":"ping"}，回 pong 防止网关/中间设备因空闲断开 */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (message.getPayload().contains("\"ping\"")) {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
                }
            }
        }
    }
}
