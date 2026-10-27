package ecommerce.auth.dto;

import lombok.Data;

/**
 * 发送登录验证码请求体。
 */
@Data
public class SendSmsCodeDTO {
    /** 手机号（11 位中国大陆手机号） */
    private String phone;
}
