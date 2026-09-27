package ecommerce.user.controller;

import ecommerce.common.Result;
import ecommerce.user.service.UserService;
import ecommerce.user.vo.UserProfileVO;
import ecommerce.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/user")
@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // 获取当前登录用户的资料：头像、昵称、基础信息一次返回
    @Operation(summary = "获取用户资料")
    @GetMapping("/profile")
    public Result<UserProfileVO> profile(HttpServletRequest request) {
        Long userId = UserContext.getUserId();
        UserProfileVO vo = userService.getProfile(userId);
        // 库里存相对路径，返回给前端拼成完整 URL 直接展示
        vo.setAvatar(fullUrl(request, vo.getAvatar()));
        return Result.success(vo);
    }

    // 上传头像：保存文件并更新 user.avatar，返回完整可访问的头像 URL
    // 这里只写 @Operation：200 由 springdoc 按返回值自动生成，400/401/403/413/500 由 OpenApiConfig 全局注入
    @Operation(summary = "上传头像")
    // 必须声明 consumes：不写的话 springdoc 会套用默认的 application/json，
    // 运行时文档里这个接口就成了 JSON 请求体（Swagger UI 不给文件选择框），空有 format: binary
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        Long userId = UserContext.getUserId();
        String relativeUrl = userService.uploadAvatar(userId, file);
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
