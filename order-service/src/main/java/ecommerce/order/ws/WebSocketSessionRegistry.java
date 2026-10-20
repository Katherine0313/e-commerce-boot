package ecommerce.order.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 后台管理端 WebSocket 会话注册表：按 shopId 分组，多商户不串店。
 * <p>
 * 单实例内存版。将来真·多实例部署时，这里要换成 Redis pub/sub 广播（每实例只推本地连接），
 * 见 task_plan 阶段 11。
 */
@Component
public class WebSocketSessionRegistry {

    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    // 连接建立：按 shopId 归组
    public void add(Long shopId, WebSocketSession session) {
        sessions.computeIfAbsent(shopId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    // 连接关闭：从对应分组移除，空分组清掉
    public void remove(Long shopId, WebSocketSession session) {
        Set<WebSocketSession> set = sessions.get(shopId);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                sessions.remove(shopId);
            }
        }
    }

    // 推送给某店铺的所有在线后台连接
    public void sendToShop(Long shopId, String message) {
        Set<WebSocketSession> set = sessions.get(shopId);
        if (set == null) {
            return;
        }
        for (WebSocketSession session : set) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (Exception e) {
                    // 发送失败忽略：连接可能已断，等重连后前端拉全量对账补漏
                }
            }
        }
    }
}
