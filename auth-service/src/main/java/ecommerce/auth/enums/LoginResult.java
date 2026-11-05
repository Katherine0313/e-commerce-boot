package ecommerce.auth.enums;

/**
 * 后台登录结果。
 * <p>
 * 枚举名直接落库到 admin_login_log.result_code，查询接口也按这个值过滤，
 * 所以改名等于改历史数据的口径，谨慎处理。
 */
public enum LoginResult {
    /** 登录成功 */
    SUCCESS,
    /** 账号不存在或密码错误（不区分，避免暴露账号是否存在） */
    BAD_CREDENTIALS,
    /** 账号已停用 */
    USER_DISABLED,
    /** 图形验证码错误 */
    CAPTCHA_ERROR,
    /** 已锁定：连续失败达上限 */
    LOCKED,
    /** 用户名或密码没传 */
    PARAM_MISSING
}
