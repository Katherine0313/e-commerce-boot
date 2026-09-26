package ecommerce.member.controller;

import ecommerce.common.Result;
import ecommerce.member.service.MemberService;
import ecommerce.member.vo.MemberProfileVO;
import ecommerce.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户资料接口：需登录（JwtInterceptor 已校验 token 并写入 UserContext）
 */
@Tag(name = "小程序用户资料", description = "微信小程序端用户资料相关接口")
@RequestMapping("/api/member")
@RestController
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    // 获取当前登录用户的资料：头像、昵称、基础信息一次返回
    @Operation(summary = "获取用户资料")
    @GetMapping("/profile")
    public Result<MemberProfileVO> profile(HttpServletRequest request) {
        Long userId = UserContext.getUserId();
        MemberProfileVO vo = memberService.getProfile(userId);
        // 库里存相对路径，返回给前端拼成完整 URL 直接展示
        vo.setAvatar(fullUrl(request, vo.getAvatar()));
        return Result.success(vo);
    }

    // 上传头像：保存文件并更新 member.avatar，返回完整可访问的头像 URL
    @Operation(summary = "上传头像")
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        Long userId = UserContext.getUserId();
        String relativeUrl = memberService.uploadAvatar(userId, file);
        return Result.success(fullUrl(request, relativeUrl));
    }

    // 相对路径拼成完整 URL；相对路径为 null 时原样返回
    private String fullUrl(HttpServletRequest request, String relativeUrl) {
        if (relativeUrl == null) {
            return null;
        }
        return request.getScheme() + "://" + request.getHeader("Host") + relativeUrl;
    }
}
