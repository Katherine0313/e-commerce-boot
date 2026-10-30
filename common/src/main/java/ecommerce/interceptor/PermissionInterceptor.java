package ecommerce.interceptor;

import ecommerce.common.annotation.RequirePerm;
import ecommerce.exception.BusinessException;
import ecommerce.utils.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 后台权限拦截器：校验 {@link RequirePerm} 声明的权限。
 * <p>
 * 必须排在 JwtInterceptor 之后，依赖它写入 UserContext 的 roles/permissions。
 * 没有权限时返回 403，由 GlobalExceptionHandler 统一转换。
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    /** 超级管理员权限标记 */
    private static final String ALL_PERMISSION = "*:*:*";

    @Override
    public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                             jakarta.servlet.http.HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequirePerm require = handlerMethod.getMethodAnnotation(RequirePerm.class);
        if (require == null) {
            require = handlerMethod.getBeanType().getAnnotation(RequirePerm.class);
        }
        if (require == null) {
            return true;
        }
        List<String> permissions = UserContext.getPermissions();
        if (permissions.contains(ALL_PERMISSION) || permissions.contains(require.value())) {
            return true;
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "无权访问该功能");
    }
}
