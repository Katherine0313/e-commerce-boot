package ecommerce.user.controller;

import ecommerce.common.Result;
import ecommerce.user.dto.WxUserLoginDTO;
import ecommerce.user.service.WxUserService;
import ecommerce.user.vo.WxUserLoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序登录接口
 */
@Tag(name = "小程序登录", description = "微信小程序端登录相关接口")
@RestController
@RequestMapping("/api")
public class WxLoginController {

    private final WxUserService wxUserService;

    public WxLoginController(WxUserService wxUserService) {
        this.wxUserService = wxUserService;
    }

    /**
     * 小程序登录：用 wx.login() 拿到的 code 换令牌
     */
    @Operation(summary = "小程序登录",
            description = "用 wx.login() 拿到的临时 code 换取令牌。首次登录自动注册，返回的 token 需放入后续请求的 Authorization 头")
    @PostMapping("/wxlogin")
    public Result<WxUserLoginVO> wxLogin(@RequestBody WxUserLoginDTO dto) {
        return Result.success(wxUserService.login(dto.getCode()));
    }
}
