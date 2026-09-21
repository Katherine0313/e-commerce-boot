package ecommerce.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 小程序用户登录入参：wx.login() 拿到的 code
 */
@Data
public class WxUserLoginDTO {
    /** 小程序 wx.login() 换来的临时凭证 */
    @Schema(description = "小程序 wx.login() 换来的临时凭证，5 分钟内有效且只能用一次",
            example = "081AbcdefGhijklmn", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;
}
