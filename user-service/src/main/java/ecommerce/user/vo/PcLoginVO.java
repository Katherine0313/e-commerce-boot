package ecommerce.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PC 商城登录结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PcLoginVO {
    /** 登录令牌，格式：Bearer {token} */
    @Schema(description = "登录令牌，后续请求放在 Authorization 头里，格式：Bearer {token}，有效期 7 天")
    private String token;

    @Schema(description = "用户 ID，仅供前端展示/缓存，不可作为鉴权依据")
    private Long id;

    @Schema(description = "昵称，可能为空")
    private String name;

    @Schema(description = "登录用户名，小程序用户可能为空")
    private String username;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "头像地址，可能为空")
    private String avatar;
}
