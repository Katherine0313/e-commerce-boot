package ecommerce.auth.dto;

import lombok.Data;

/**
 * 手机号验证码登录请求体。
 */
@Data
public class PhoneLoginDTO {
    /** 手机号（11 位中国大陆手机号） */
    private String phone;
    /** 短信验证码（6 位数字） */
    private String code;
}
