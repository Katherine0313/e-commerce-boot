package ecommerce.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * PC 账号密码登录入参。
 * <p>
 * 账号支持两种：用户名（PC 注册时设置）或手机号。
 * 新前端统一传 {@code account}；旧前端仍传 {@code phone}，服务端两者都认。
 */
@Data
public class PcLoginDTO {

    @Schema(description = "登录账号：用户名或手机号（与 phone 二选一，优先取本字段）",
            example = "jamie", requiredMode = Schema.RequiredMode.REQUIRED)
    private String account;

    @Schema(description = "手机号（旧前端传本字段；新前端请改用 account）", example = "13800138000")
    private String phone;

    @Schema(description = "密码（6-20 位）", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
