package ecommerce.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图形验证码响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaVO {
    @Schema(description = "验证码 ID，登录时回传")
    private String captchaId;
    @Schema(description = "验证码图片（data:image/png;base64,...）")
    private String image;
}
