package ecommerce.shop.controller;

import ecommerce.common.Result;
import ecommerce.shop.service.ShopService;
import ecommerce.shop.vo.SandboxVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店铺接口（公开）。
 * <p>
 * /api/shops/sandbox 是公开接口（免登录，见 common 的 EXCLUDE_AUTH_PATHS），游客一键体验开沙箱。
 * 当前店铺信息走后台接口 /api/admin/shops/current（见 AdminShopController）。
 */
@Tag(name = "店铺", description = "开沙箱")
@RestController
@RequestMapping("/api/shops")
public class ShopController {

    /** 浏览器访客标识请求头：同一 key 24h 内复用同一沙箱店 */
    public static final String VISITOR_KEY_HEADER = "X-Sandbox-Visitor-Key";

    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @Operation(summary = "一键体验：复用或新开沙箱店（克隆模板店数据 + 签发 admin 令牌）")
    @Parameter(name = VISITOR_KEY_HEADER, in = ParameterIn.HEADER, required = false,
            description = "浏览器访客标识（UUID），用于 24 小时内复用同一沙箱店；缺失则每次新建")
    @PostMapping("/sandbox")
    public Result<SandboxVO> createSandbox(
            @RequestHeader(value = VISITOR_KEY_HEADER, required = false) String visitorKey) {
        return Result.success(shopService.createSandbox(visitorKey));
    }
}
