package ecommerce.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 发送验证码响应。
 * devCode 只在开发模式（sms.dev-mode=true）下返回，方便本机联调；生产环境恒为 null。
 */
@Data
@Builder
public class SmsCodeVO {
    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "验证码有效期（秒）")
    private long expireIn;

    @Schema(description = "开发环境验证码；生产环境为 null")
    private String devCode;
}
