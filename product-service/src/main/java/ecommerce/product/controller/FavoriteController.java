package ecommerce.product.controller;

import ecommerce.common.Result;
import ecommerce.product.service.FavoriteService;
import ecommerce.product.vo.FavoriteVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品收藏接口（C 端，需登录）。
 * <p>
 * /api/favorites/** 不在 common 的 EXCLUDE_AUTH_PATHS 白名单内，JwtInterceptor 自动要求 user 令牌。
 */
@Tag(name = "商品收藏", description = "C 端商品收藏接口（需登录）")
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @Operation(summary = "收藏列表（按收藏时间倒序，含已下架商品）")
    @GetMapping
    public Result<List<FavoriteVO>> list() {
        return Result.success(favoriteService.list());
    }

    @Operation(summary = "是否已收藏（详情页回显）")
    @GetMapping("/check")
    public Result<Boolean> check(@RequestParam Long productId) {
        return Result.success(favoriteService.check(productId));
    }

    @Operation(summary = "收藏商品（重复收藏幂等）")
    @PostMapping("/{productId}")
    public Result<Void> add(@PathVariable Long productId) {
        favoriteService.add(productId);
        return Result.success();
    }

    @Operation(summary = "取消收藏（不存在也返回成功）")
    @DeleteMapping("/{productId}")
    public Result<Void> remove(@PathVariable Long productId) {
        favoriteService.remove(productId);
        return Result.success();
    }
}
