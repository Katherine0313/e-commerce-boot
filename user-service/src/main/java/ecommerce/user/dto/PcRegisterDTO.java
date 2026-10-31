package ecommerce.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * PC 账号密码注册入参。
 * <p>
 * 用户名 + 手机号 + 密码三者必填：用户名用于账号密码登录，
 * 手机号用于手机号验证码登录（两者指向同一个账号）。
 */
@Data
public class PcRegisterDTO {

    @Schema(description = "用户名（3-30 位字母/数字/下划线，全局唯一，统一转小写存储）",
            example = "jamie", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @Schema(description = "手机号", example = "13800138000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String phone;

    @Schema(description = "密码（6-20 位）", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
