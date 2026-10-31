package ecommerce.user.controller;

import ecommerce.common.Result;
import ecommerce.user.dto.PcCodeLoginDTO;
import ecommerce.user.dto.PcLoginDTO;
import ecommerce.user.dto.PcRegisterDTO;
import ecommerce.user.dto.PcSendCodeDTO;
import ecommerce.user.service.PcAuthService;
import ecommerce.user.vo.PcLoginVO;
import ecommerce.user.vo.PcSendCodeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PC 商城登录/注册。
 * 验证码为本地模拟（不发真实短信），相关接口已加入免登录白名单。
 */
@Tag(name = "PC 商城登录", description = "账号密码 / 手机验证码登录注册（验证码为本地模拟）")
@RestController
@RequestMapping("/api/user")
public class PcAuthController {

    private final PcAuthService pcAuthService;

    public PcAuthController(PcAuthService pcAuthService) {
        this.pcAuthService = pcAuthService;
    }

    @Operation(summary = "账号密码登录（用户名 + 密码）")
    @PostMapping("/login")
    public Result<PcLoginVO> login(@RequestBody PcLoginDTO dto) {
        return Result.success(pcAuthService.loginByPassword(dto));
    }

    @Operation(summary = "账号密码注册（用户名 + 手机号 + 密码）")
    @PostMapping("/register")
    public Result<PcLoginVO> register(@RequestBody PcRegisterDTO dto) {
        return Result.success(pcAuthService.register(dto));
    }

    @Operation(summary = "发送登录验证码（模拟）")
    @PostMapping("/send-code")
    public Result<PcSendCodeVO> sendCode(@RequestBody PcSendCodeDTO dto) {
        return Result.success(pcAuthService.sendLoginCode(dto));
    }

    @Operation(summary = "手机号验证码登录")
    @PostMapping("/login-code")
    public Result<PcLoginVO> loginByCode(@RequestBody PcCodeLoginDTO dto) {
        return Result.success(pcAuthService.loginByCode(dto));
    }
}
