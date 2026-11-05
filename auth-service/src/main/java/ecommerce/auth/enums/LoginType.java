package ecommerce.auth.enums;

/**
 * 后台登录日志的动作类型。
 * <p>
 * 独立建表（admin_login_log）是因为 admin 表没有 last_login_time / login_ip 之类字段，
 * 登录失败计数只在 Redis 里，不留审计痕迹。
 */
public enum LoginType {
    /** 登录 */
    LOGIN,
    /** 登出 */
    LOGOUT
}
