package ecommerce.user.controller;

import ecommerce.common.Result;
import ecommerce.user.dto.WxLoginDTO;
import ecommerce.user.service.UserService;
import ecommerce.user.vo.WxLoginVO;
import ecommerce.utils.UrlUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@Tag(name = "小程序登录", description = "微信小程序端登录相关接口")
@RequestMapping("/api")
@RestController
public class WxLoginController {
    public final UserService userService;
    public WxLoginController(UserService userService){
        this.userService = userService;
    }
    // 小程序登录：用wx.login()拿到的code换取令牌
    // 这里只写 @Operation：200 由 springdoc 按返回值自动生成，400/401/403 由 OpenApiConfig 全局注入
    @Operation(summary = "微信登陆")
    @PostMapping("/wxlogin")
    public Result<WxLoginVO> wxLogin(@RequestBody WxLoginDTO dto, HttpServletRequest request) {
        WxLoginVO vo = userService.login(dto.getCode());
        // 库里存的是相对路径，拼成完整 URL 再返回，与 /api/user/profile 保持一致，
        // 否则前端拿到 /upload/avatar/xxx 会被小程序当成包内路径（渲染层加载失败）
        vo.setAvatar(UrlUtil.fullUrl(request, vo.getAvatar()));
        return Result.success(vo);
    }
}
