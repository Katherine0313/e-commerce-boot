package ecommerce.system.service;

import ecommerce.security.AdminOnlineSessionStore;
import ecommerce.security.AdminTokenRevocationStore;
import ecommerce.utils.IpMasker;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在线用户：读取登录时登记的 Redis 会话（auth:online:{adminId}:{sid}）。
 * <p>
 * 一期只做到账号级强制下线（复用 invalid-before 机制），页面文案需写清楚。
 */
@Service
public class OnlineUserService {

    private static final int MAX_SESSIONS = 5000;

    private final AdminOnlineSessionStore onlineSessionStore;
    private final AdminTokenRevocationStore revocationStore;

    public OnlineUserService(AdminOnlineSessionStore onlineSessionStore,
                             AdminTokenRevocationStore revocationStore) {
        this.onlineSessionStore = onlineSessionStore;
        this.revocationStore = revocationStore;
    }

    public Map<String, Object> list(String username, String ip) {
        List<Map<String, Object>> sessions = new ArrayList<>();
        for (String key : onlineSessionStore.scanSessionKeys()) {
            if (sessions.size() >= MAX_SESSIONS) {
                break;
            }
            Map<String, String> raw = onlineSessionStore.readSession(key);
            if (raw.isEmpty()) {
                continue;
            }
            Map<String, Object> session = new LinkedHashMap<>();
            session.put("adminId", parseLong(raw.get("adminId")));
            session.put("username", raw.getOrDefault("username", ""));
            session.put("ip", IpMasker.mask(raw.getOrDefault("ip", "")));
            session.put("userAgent", truncate(raw.getOrDefault("userAgent", ""), 120));
            session.put("roles", raw.getOrDefault("roles", ""));
            session.put("loginTime", toTime(raw.get("loginTime")));
            session.put("lastActive", toTime(raw.get("lastActive")));
            if (matches(session, username, ip)) {
                sessions.add(session);
            }
        }
        sessions.sort(Comparator.comparing(
                (Map<String, Object> item) -> (LocalDateTime) item.get("loginTime"),
                Comparator.nullsLast(Comparator.reverseOrder())));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", sessions.size());
        result.put("list", sessions);
        return result;
    }

    /** 强制下线：删除该账号全部会话 + 让已签发 token 立即失效 */
    public int forceLogout(Long adminId) {
        int removed = onlineSessionStore.removeAll(adminId);
        revocationStore.invalidate(adminId);
        return removed;
    }

    private boolean matches(Map<String, Object> session, String username, String ip) {
        if (username != null && !username.isBlank()
                && !String.valueOf(session.get("username")).toLowerCase()
                .contains(username.trim().toLowerCase())) {
            return false;
        }
        // IP 已脱敏，按脱敏后的文本匹配（不支持精确 IP 查询）
        return ip == null || ip.isBlank() || String.valueOf(session.get("ip")).contains(ip.trim());
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDateTime toTime(String millis) {
        if (millis == null || millis.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(millis.trim())),
                    ZoneId.systemDefault());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
