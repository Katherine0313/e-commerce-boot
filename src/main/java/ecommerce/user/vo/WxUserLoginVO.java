package ecommerce.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 小程序用户登录出参：返回给小程序前端
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WxUserLoginVO {
    /** 登录令牌，后续请求放在 Authorization 头里 */
    @Schema(description = "登录令牌，后续请求放在 Authorization 头里，格式：Bearer {token}，有效期 7 天")
    private String token;
    /** 用户 ID，仅供前端展示/缓存，不可作为鉴权依据 */
    @Schema(description = "用户 ID，仅供前端展示/缓存，不可作为鉴权依据")
    private Long id;
    /** 用户昵称 */
    @Schema(description = "用户昵称，首次登录时为 null")
    private String name;
    /** 头像地址 */
    @Schema(description = "头像地址，首次登录时为 null")
    private String avatar;
}
