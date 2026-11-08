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

    /** 共享密钥：默认仅用于本地开发，生产必须靠环境变量 AI_INTERNAL_TOKEN 覆盖 */
    private final String internalToken;

    public InternalApiInterceptor(@Value("${ai.internal.token:change-me-in-prod}") String internalToken) {
        this.internalToken = internalToken;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
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
