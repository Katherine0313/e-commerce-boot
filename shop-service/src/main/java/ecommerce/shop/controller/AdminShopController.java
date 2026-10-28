package ecommerce.shop.controller;

import ecommerce.common.Result;
import ecommerce.shop.service.ShopService;
import ecommerce.shop.vo.SandboxVO;
import ecommerce.shop.vo.ShopVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台店铺接口（/api/admin/shops）。
 * <p>
 * 路径 /api/admin/** 由 JwtInterceptor 按 admin 身份分流校验，需 admin 令牌。
 * 一键体验开沙箱也放在这里：必须先通过手机号验证码登录拿到 admin 令牌，不能再匿名开。
 */
@Tag(name = "后台店铺", description = "当前店铺信息 / 一键体验开沙箱（管理员）")
@RestController
@RequestMapping("/api/admin/shops")
public class AdminShopController {

    /** 浏览器访客标识请求头：同一 key 24h 内复用同一沙箱店 */
    private static final String VISITOR_KEY_HEADER = "X-Sandbox-Visitor-Key";

    private final ShopService shopService;

    public AdminShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @Operation(summary = "当前店铺信息")
    @GetMapping("/current")
    public Result<ShopVO> getCurrentShop() {
        return Result.success(shopService.getCurrentShop());
    }

    @Operation(summary = "一键体验：复用或新开沙箱店（需先通过手机号验证码登录）")
    @Parameter(name = VISITOR_KEY_HEADER, in = ParameterIn.HEADER, required = false,
            description = "浏览器访客标识（UUID），用于 24 小时内复用同一沙箱店；缺失则每次新建")
    @PostMapping("/sandbox")
    public Result<SandboxVO> createSandbox(
            @RequestHeader(value = VISITOR_KEY_HEADER, required = false) String visitorKey) {
        return Result.success(shopService.createSandbox(visitorKey));
    }
}
