package ecommerce.member.controller;

import ecommerce.common.Result;
import ecommerce.member.dto.WxLoginDTO;
import ecommerce.member.service.MemberService;
import ecommerce.member.vo.WxLoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@Tag(name = "小程序登录", description = "微信小程序端登录相关接口")
@RequestMapping("/api")
@RestController
public class WxLoginController {
    public final MemberService memberService;
    public WxLoginController(MemberService memberService){
        this.memberService = memberService;
    }
    // 小程序登录：用wx.login()拿到的code换取令牌
    @Operation(summary = "微信登陆")
    @PostMapping("/wxlogin")
    public Result<WxLoginVO> wxLogin(@RequestBody WxLoginDTO dto) {
        return Result.success(memberService.login(dto.getCode()));
    }
}
