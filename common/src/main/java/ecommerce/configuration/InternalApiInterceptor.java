package ecommerce.configuration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * AI 内部接口（/internal/ai/**）共享密钥鉴权拦截器。
 * <p>
 * 调用方固定为内网 Python AI 服务（Python → 网关 → Java），不走 JWT：
 * common.WebMvcConfig.EXCLUDE_AUTH_PATHS 已把 /internal/** 排除在登录拦截之外，
 * 这里单独校验 X-Internal-Token 头与配置 ai.internal.token 相等，防止外部直调
 * （原有的 X-Internal-Call: true 是静态字符串，公网可伪造，AI 接口暴露订单/售后，风险更高）。
 * <p>
 * 校验失败直接写 401 JSON，不进 Controller，也不走全局异常处理器。
 */
@Slf4j
@Component
public class InternalApiInterceptor implements HandlerInterceptor {

    /** 内部调用共享密钥请求头 */
    private static final String TOKEN_HEADER = "X-Internal-Token";

    /**
     * 共享密钥：生产必须靠环境变量 AI_INTERNAL_TOKEN 注入（Spring 宽松绑定到 ai.internal.token）。
     * 不给兜底默认值 —— 未配置时一律拒绝（fail-closed），避免「两边都用同一个可猜测默认值」
     * 导致内部接口实际处于无鉴权状态。
     */
    private final String internalToken;

    public InternalApiInterceptor(@Value("${ai.internal.token:}") String internalToken) {
        this.internalToken = internalToken == null ? "" : internalToken.trim();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // 服务端没配共享密钥：一律拒绝，并打 error 日志提示去补环境变量
        if (internalToken.isEmpty()) {
            log.error("AI 内部接口未配置共享密钥（ai.internal.token / 环境变量 AI_INTERNAL_TOKEN），已拒绝请求: uri={}",
                    request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":503,\"message\":\"internal token not configured\",\"data\":null}");
            return false;
        }
        if (internalToken.equals(request.getHeader(TOKEN_HEADER))) {
            return true;
        }
        log.warn("AI 内部接口鉴权失败: uri={}", request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"forbidden\",\"data\":null}");
        return false;
    }
}
