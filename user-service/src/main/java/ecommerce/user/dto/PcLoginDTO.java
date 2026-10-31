package ecommerce.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * PC 账号密码登录入参。
 * <p>
 * 只认用户名；手机号登录走「手机号 + 验证码」（{@code PcCodeLoginDTO}），两者不混用。
 */
@Data
public class PcLoginDTO {

    @Schema(description = "用户名（3-30 位字母/数字/下划线）", example = "jamie",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @Schema(description = "密码（6-20 位）", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
