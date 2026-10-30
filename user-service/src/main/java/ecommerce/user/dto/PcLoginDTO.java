package ecommerce.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * PC 账号密码登录 / 注册入参
 */
@Data
public class PcLoginDTO {
    @Schema(description = "手机号", example = "13800138000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String phone;

    @Schema(description = "密码（6-20 位）", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
