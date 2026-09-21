package ecommerce.controller;

import ecommerce.common.Result;
import ecommerce.dto.WxUserLoginDTO;
import ecommerce.service.WxUserService;
import ecommerce.vo.WxUserLoginVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序登录接口
 */
@RestController
@RequestMapping("/api")
public class WXLoginController {

    private final WxUserService wxUserService;

    public WXLoginController(WxUserService wxUserService) {
        this.wxUserService = wxUserService;
    }

    /**
     * 小程序登录：用 wx.login() 拿到的 code 换令牌
     */
    @PostMapping("/wxlogin")
    public Result<WxUserLoginVO> wxlogin(@RequestBody WxUserLoginDTO dto) {
        return Result.success(wxUserService.login(dto.getCode()));
    }
}
