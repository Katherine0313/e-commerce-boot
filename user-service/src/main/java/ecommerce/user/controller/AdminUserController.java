package ecommerce.user.controller;

import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.user.service.UserService;
import ecommerce.user.vo.UserStatisticsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台用户管理接口（/api/admin/users）。
 * <p>
 * 路径 /api/admin/** 由 JwtInterceptor 按 admin 身份分流校验，需 admin 令牌。
 */
@Tag(name = "后台用户", description = "用户增长统计（管理员）")
@RestController
@RequirePerm("workplace:view")
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "用户增长统计（period：yesterday/week/month，默认近 12 月）")
    @GetMapping("/statistics")
    public Result<UserStatisticsVO> statistics(@RequestParam(defaultValue = "year") String period) {
        return Result.success(userService.getUserStatistics(period));
    }
}
