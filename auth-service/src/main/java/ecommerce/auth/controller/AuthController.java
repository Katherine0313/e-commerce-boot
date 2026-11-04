package ecommerce.auth.controller;

import ecommerce.auth.dto.AdminLoginDTO;
import ecommerce.auth.service.AuthService;
import ecommerce.auth.vo.AdminLoginVO;
import ecommerce.auth.vo.AdminUserVO;
import ecommerce.auth.vo.CaptchaVO;
import ecommerce.common.Result;
import ecommerce.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台登录", description = "管理员登录/登出/当前用户")
@RequestMapping("/api")
@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "管理员登录")
    @PostMapping("/login")
    public Result<AdminLoginVO> login(@RequestBody AdminLoginDTO dto, HttpServletRequest request) {
        return Result.success(authService.login(dto, resolveClientIp(request)));
    }

    /** 取客户端 IP：EdgeOne 回源时注入的 EO-Connecting-IP 最可信，其次 X-Forwarded-For / X-Real-IP */
    private String resolveClientIp(HttpServletRequest request) {
        String eoIp = request.getHeader("EO-Connecting-IP");
        if (eoIp != null && !eoIp.isBlank()) {
            return eoIp.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    @Operation(summary = "获取图形验证码")
    @GetMapping("/auth/captcha")
    public Result<CaptchaVO> captcha() {
        return Result.success(authService.createCaptcha());
    }

    @Operation(summary = "当前登录管理员信息")
    @GetMapping("/admin/info")
    public Result<AdminUserVO> userInfo() {
        return Result.success(authService.getInfo(UserContext.getUserId()));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout() {
        // 无状态 JWT：服务端无需作废令牌，前端删除本地 token 即可
        return Result.success();
    }
}