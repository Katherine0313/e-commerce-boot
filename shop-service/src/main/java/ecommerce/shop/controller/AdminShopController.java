package ecommerce.shop.controller;

import ecommerce.common.Result;
import ecommerce.shop.service.ShopService;
import ecommerce.shop.vo.ShopVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台店铺接口（/api/admin/shops）。
 * <p>
 * 路径 /api/admin/** 由 JwtInterceptor 按 admin 身份分流校验，需 admin 令牌。
 */
@Tag(name = "后台店铺", description = "当前店铺信息（管理员）")
@RestController
@RequestMapping("/api/admin/shops")
public class AdminShopController {
    private final ShopService shopService;

    public AdminShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @Operation(summary = "当前店铺信息")
    @GetMapping("/current")
    public Result<ShopVO> getCurrentShop() {
        return Result.success(shopService.getCurrentShop());
    }
}
