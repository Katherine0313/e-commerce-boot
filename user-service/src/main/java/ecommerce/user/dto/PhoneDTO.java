package ecommerce.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 绑定手机号入参。
 * <p>
 * - 微信小程序：只传 getPhoneNumber 授权后的 code（phone 为空）
 * - PC 商城：传 phone + PC 短信验证码 code
 */
@Data
public class PhoneDTO {
    /** getPhoneNumber 授权后 e.detail.code，5 分钟内有效且只能用一次（微信端） */
    @Schema(description = "微信 getPhoneNumber 授权后拿到的动态令牌；PC 端不传",
            example = "abcd1234efgh")
    private String code;

    /** PC 端要绑定的手机号；为空表示走微信授权流程 */
    @Schema(description = "PC 端绑定的手机号（11 位），不传则按微信 code 换手机号",
            example = "13800138000")
    private String phone;
}
