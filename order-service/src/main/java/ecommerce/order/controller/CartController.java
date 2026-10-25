package ecommerce.order.controller;

import ecommerce.common.Result;
import ecommerce.order.dto.AddCartDTO;
import ecommerce.order.dto.ClearCartDTO;
import ecommerce.order.dto.UpdateQuantityDTO;
import ecommerce.order.service.CartService;
import ecommerce.order.vo.CartVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 购物车接口（需登录）。
 * <p>
 * /api/cart/** 不在 common 的 EXCLUDE_AUTH_PATHS 白名单内，JwtInterceptor 会自动要求 user 令牌；
 * 当前用户从 UserContext.getUserId() 取。
 */
@Tag(name = "购物车", description = "购物车接口（需登录）")
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @Operation(summary = "购物车列表")
    @GetMapping
    public Result<List<CartVO>> getCart() {
        return Result.success(cartService.getCart());
    }

    @Operation(summary = "购物车商品总件数（首页角标）")
    @GetMapping("/count")
    public Result<Integer> getCount() {
        return Result.success(cartService.getCount());
    }

    @Operation(summary = "加入购物车")
    @PostMapping
    public Result<Void> addItem(@RequestBody AddCartDTO dto) {
        cartService.addItem(dto);
        return Result.success();
    }

    @Operation(summary = "更新数量")
    @PutMapping("/{productId}")
    public Result<Void> updateQuantity(@PathVariable Long productId,
                                       @RequestBody UpdateQuantityDTO dto) {
        cartService.updateQuantity(productId, dto.getQuantity());
        return Result.success();
    }

    @Operation(summary = "移除某个商品")
    @DeleteMapping("/{productId}")
    public Result<Void> removeItem(@PathVariable Long productId) {
        cartService.removeItem(productId);
        return Result.success();
    }

    @Operation(summary = "清空购物车（可选 body.productIds 批量删除）")
    @DeleteMapping
    public Result<Void> clear(@RequestBody(required = false) ClearCartDTO dto) {
        List<Long> productIds = dto == null ? null : dto.getProductIds();
        cartService.clear(productIds);
        return Result.success();
    }
}