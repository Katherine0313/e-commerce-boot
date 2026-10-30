package ecommerce.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * PC 手机号验证码登录入参
 */
@Data
public class PcCodeLoginDTO {
    @Schema(description = "手机号", example = "13800138000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String phone;

    @Schema(description = "6 位验证码", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;
}
