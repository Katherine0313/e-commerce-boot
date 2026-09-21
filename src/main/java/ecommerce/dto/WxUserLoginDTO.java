package ecommerce.dto;

import lombok.Data;

/**
 * 小程序用户登录入参：wx.login() 拿到的 code
 */
@Data
public class WxUserLoginDTO {
    /** 小程序 wx.login() 换来的临时凭证 */
    private String code;
}
