package ecommerce.interceptor;

import ecommerce.exception.BusinessException;
import ecommerce.security.AdminTokenRevocationStore;
import ecommerce.utils.JwtUtil;
import ecommerce.utils.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Objects;

/**
 * 登录鉴权拦截器
 * <p>
 * 流程：取 Authorization 头 → 校验令牌 → 比对身份类型 → 写入 UserContext。
 * 校验失败直接抛 BusinessException(401/403)，由 GlobalExceptionHandler 统一转成 Result 返回，
 * 所以前端拿到的仍然是 {success, data, message} 结构，只是 HTTP 状态码是 401/403。
 */
@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

    /** 令牌请求头 */
    private static final String HEADER = "Authorization";
    /** 令牌前缀，兼容前端直接传原始 token 的写法 */
    private static final String BEARER_PREFIX = "Bearer ";
    /** 后台管理系统接口的路径前缀 */
    private static final String ADMIN_PATH = "/api/admin";

    private final JwtUtil jwtUtil;
    private final AdminTokenRevocationStore revocationStore;

    public JwtInterceptor(JwtUtil jwtUtil, AdminTokenRevocationStore revocationStore) {

        this.jwtUtil = jwtUtil;
        this.revocationStore = revocationStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 只处理打到 Controller 方法上的请求；静态资源、CORS 预检的 handler 不是 HandlerMethod，直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String token = resolveToken(request);
        if (token == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }

        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (JwtException | IllegalArgumentException e) {
            // 签名不对 / 已过期 / 格式非法，对前端来说都是「登录失效」；日志里不打 token 本身
            log.warn("令牌校验失败: {}", e.getMessage());
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }

        // 按路径分流要求的身份类型。必须校验：否则顾客令牌能直接调后台管理接口
        String requiredType = isAdminPath(request) ? JwtUtil.TYPE_ADMIN : JwtUtil.TYPE_USER;
        String actualType = claims.get("type", String.class);
        if (!requiredType.equals(actualType)) {
            log.warn("令牌身份类型不匹配: 需要 {}, 实际 {}", requiredType, actualType);
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权访问");
        }

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "登录信息不完整，请重新登录");
        }
        Long userId = Long.valueOf(subject);
        // 管理员令牌：角色/菜单/账号状态变更后，旧 token 立即失效（Redis 记录失效时间点）
        if (JwtUtil.TYPE_ADMIN.equals(actualType)) {
            java.util.Date issuedAt = claims.getIssuedAt();
            if (issuedAt == null || revocationStore.isInvalid(userId, issuedAt.toInstant().getEpochSecond())) {
                log.info("管理员令牌已失效: adminId={}", userId);
                throw new BusinessException(HttpStatus.UNAUTHORIZED, "登录状态已变更，请重新登录");
            }
        }
        // 存入本次请求的上下文，Controller / Service 里用 UserContext 取
        UserContext.setUserId(userId);
        // 身份类型：admin / user，操作日志要区分「后台管理员」与「买家」
        UserContext.setUserType(actualType);
        // 账号快照：admin 令牌带 username claim，操作日志要留操作人账号（账号可能被改名/软删）
        String username = claims.get("username", String.class);
        if (username != null && !username.isBlank()) {
            UserContext.setUsername(username);
        }
        // 后台令牌里带了 roles/permissions，后续 PermissionInterceptor 直接读上下文
        UserContext.setAuth(readStringList(claims.get("roles")), readStringList(claims.get("permissions")));
        return true;
    }

    /** JWT 里的数组 claim 统一转成字符串列表 */
    private List<String> readStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(Objects::toString).toList();
        }
        return List.of();
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 必须清：工作线程会被复用，不清会把 userId 泄露给下一个请求
        UserContext.remove();
    }

    /** 从 Authorization 头取令牌，带不带 "Bearer " 前缀都能识别；取不到返回 null */
    private String resolveToken(HttpServletRequest request) {
        String value = request.getHeader(HEADER);
        if (value == null || value.isBlank()) {
            return null;
        }
        value = value.trim();
        return value.startsWith(BEARER_PREFIX)
                ? value.substring(BEARER_PREFIX.length()).trim()
                : value;
    }

    /** 是否属于后台管理系统的接口，后台接入后按这里的分流规则走 */
    private boolean isAdminPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.equals(ADMIN_PATH) || uri.startsWith(ADMIN_PATH + "/");
    }
}
