package ecommerce.vo;

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
    private String token;
    /** 用户 ID，仅供前端展示/缓存，不可作为鉴权依据 */
    private Long id;
    /** 用户昵称 */
    private String name;
    /** 头像地址 */
    private String avatar;
}
