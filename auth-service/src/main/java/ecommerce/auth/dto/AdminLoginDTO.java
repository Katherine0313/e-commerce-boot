package ecommerce.auth.dto;

import lombok.Data;

/**
 * 管理员登录请求体。
 */
@Data
public class AdminLoginDTO {

    private String username;
    private String password;
    /** 图形验证码 ID（GET /api/auth/captcha 返回） */
    private String captchaId;
    /** 用户输入的图形验证码 */
    private String captchaCode;
}
