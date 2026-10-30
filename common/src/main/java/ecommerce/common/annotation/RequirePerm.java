package ecommerce.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 后台接口权限校验注解。
 * <p>
 * 例：{@code @RequirePerm("product:product:list")}。
 * 权限来自登录令牌 payload，由 PermissionInterceptor 校验。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerm {
    /** 权限标识，如 product:product:list */
    String value();
}
