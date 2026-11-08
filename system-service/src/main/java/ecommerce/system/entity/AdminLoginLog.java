package ecommerce.system.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 后台登录/登出日志实体（对应 admin_login_log 表）。
 * <p>
 * username 记用户输入的原样 trim 值：账号根本不存在时也要留下，
 * 否则暴力破解的账号名查不出来。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLoginLog {
    private Long id;
    /** 管理员 ID；账号不存在时为 null */
    private Long adminId;
    /** 登录名（原样保留，便于排查爆破） */
    private String username;
    /** LOGIN / LOGOUT */
    private String loginType;
    /** SUCCESS / BAD_CREDENTIALS / USER_DISABLED / CAPTCHA_ERROR / LOCKED / PARAM_MISSING */
    private String resultCode;
    /** 失败原因文本 */
    private String reason;
    /** 客户端 IP */
    private String ip;
    /** 浏览器 User-Agent */
    private String userAgent;
    /** 发生时间（Java 传参，不依赖 DB 的 NOW()） */
    private LocalDateTime createdAt;
}
