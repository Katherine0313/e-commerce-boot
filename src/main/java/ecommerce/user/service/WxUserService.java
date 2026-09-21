package ecommerce.user.service;

import ecommerce.user.vo.WxUserLoginVO;

/**
 * 小程序用户业务接口
 */
public interface WxUserService {
    /**
     * 小程序登录：code 换 openid，查不到就自动注册，返回令牌
     *
     * @param code 小程序 wx.login() 拿到的临时凭证
     */
    WxUserLoginVO login(String code);
}
