package ecommerce.order.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 后台管理端 WebSocket 会话注册表（单店模式，所有在线后台连接共享一个集合）。
 * <p>
 * 单实例内存版。将来真·多实例部署时，这里要换成 Redis pub/sub 广播（每实例只推本地连接），
 * 见 task_plan 阶段 11。
 */
@Component
public class WebSocketSessionRegistry {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    // 连接建立
    public void add(WebSocketSession session) {
        sessions.add(session);
    }

    // 连接关闭
    public void remove(WebSocketSession session) {
        sessions.remove(session);
    }

    // 推送给所有在线后台连接
    public void broadcast(String message) {
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                // 连接已关闭但未触发 afterConnectionClosed（半开连接），顺手清掉
                sessions.remove(session);
                continue;
            }
            try {
                // WebSocketSession 非线程安全，多个订单同时支付时串行发送，避免并发写导致漏推
                synchronized (session) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (Exception e) {
                // 发送失败视为连接失效，移除以免后续推送继续失败
                sessions.remove(session);
            }
        }
    }
}
