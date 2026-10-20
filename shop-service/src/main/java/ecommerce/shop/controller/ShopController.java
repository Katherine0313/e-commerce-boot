package ecommerce.shop.controller;

import ecommerce.common.Result;
import ecommerce.shop.service.ShopService;
import ecommerce.shop.vo.SandboxVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
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

    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @Operation(summary = "一键体验：开沙箱店（克隆模板店数据 + 签发 admin 令牌）")
    @PostMapping("/sandbox")
    public Result<SandboxVO> createSandbox() {
        return Result.success(shopService.createSandbox());
    }
}
