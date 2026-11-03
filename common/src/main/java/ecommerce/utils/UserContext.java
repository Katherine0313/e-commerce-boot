package ecommerce.utils;

import java.util.List;

/**
 * 当前登录用户上下文
 * <p>
 * 用 ThreadLocal 保存「本次请求」的登录信息：JwtInterceptor 在 preHandle 里写入，
 * afterCompletion 里清除。Tomcat 的工作线程是复用的，这两个动作必须成对出现，
 * 否则下一个请求会读到上一个用户的 userId。
 */
public class UserContext {

    /** 当前登录用户 ID，未登录时为 null */
    private static final ThreadLocal<Long> CURRENT_ID = new ThreadLocal<>();
    /** 当前登录人身份类型：admin（后台）/ user（小程序、PC 商城）/ null（未登录） */
    private static final ThreadLocal<String> CURRENT_TYPE = new ThreadLocal<>();
    /** 后台角色标识（如 admin/operator/service/warehouse） */
    private static final ThreadLocal<List<String>> CURRENT_ROLES = new ThreadLocal<>();
    /** 后台权限标识（如 product:product:list） */
    private static final ThreadLocal<List<String>> CURRENT_PERMISSIONS = new ThreadLocal<>();

    /** 工具类，禁止实例化 */
    private UserContext() {
    }

    /** 保存当前登录用户 ID，由 JwtInterceptor 调用 */
    public static void setUserId(Long userId) {
        CURRENT_ID.set(userId);
    }

    /** 获取当前登录用户 ID，未登录返回 null */
    public static Long getUserId() {

        return CURRENT_ID.get();
    }

    /** 保存当前登录人身份类型（admin / user），由 JwtInterceptor 调用 */
    public static void setUserType(String userType) {
        CURRENT_TYPE.set(userType);
    }

    /** 获取当前登录人身份类型，未登录返回 null */
    public static String getUserType() {
        return CURRENT_TYPE.get();
    }

    /** 保存后台角色与权限，由 JwtInterceptor 从令牌 claims 写入 */
    public static void setAuth(List<String> roles, List<String> permissions) {
        CURRENT_ROLES.set(roles == null ? List.of() : roles);
        CURRENT_PERMISSIONS.set(permissions == null ? List.of() : permissions);
    }

    public static List<String> getRoles() {
        List<String> roles = CURRENT_ROLES.get();
        return roles == null ? List.of() : roles;
    }

    public static List<String> getPermissions() {
        List<String> permissions = CURRENT_PERMISSIONS.get();
        return permissions == null ? List.of() : permissions;
    }

    /** 清除本次请求的登录信息，必须由 JwtInterceptor 在请求结束时调用 */
    public static void remove() {
        CURRENT_ID.remove();
        CURRENT_TYPE.remove();
        CURRENT_ROLES.remove();
        CURRENT_PERMISSIONS.remove();
    }
}
