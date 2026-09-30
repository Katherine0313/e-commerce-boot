package ecommerce.auth.controller;

import ecommerce.auth.dto.AdminLoginDTO;
import ecommerce.auth.service.AuthService;
import ecommerce.auth.vo.AdminLoginVO;
import ecommerce.auth.vo.AdminUserVO;
import ecommerce.common.Result;
import ecommerce.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    public Result<AdminLoginVO> login(@RequestBody AdminLoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    @Operation(summary = "当前登录管理员信息")
    @GetMapping("/user/info")
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
