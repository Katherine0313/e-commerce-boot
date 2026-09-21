package ecommerce.utils;

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

    /** 清除本次请求的登录信息，必须由 JwtInterceptor 在请求结束时调用 */
    public static void remove() {
        CURRENT_ID.remove();
    }
}
