package ecommerce.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 绑定手机号入参：getPhoneNumber 按钮授权后拿到的 code
 */
@Data
public class PhoneDTO {
    /** getPhoneNumber 授权后 e.detail.code，5 分钟内有效且只能用一次 */
    @Schema(description = "getPhoneNumber 授权后拿到的动态令牌，5 分钟内有效且只能用一次",
            example = "abcd1234efgh", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;
}
