package ecommerce.product.controller;

import ecommerce.common.Result;
import ecommerce.product.service.ProductService;
import ecommerce.product.vo.CategoryVO;
import ecommerce.product.vo.ProductVO;
import ecommerce.product.vo.TagVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品浏览接口（免登录）。
 * <p>
 * 这些接口在 common 的 WebMvcConfig.EXCLUDE_AUTH_PATHS 白名单内，不带 token 也能访问；
 * 加购 / 下单走 order-service，才需要登录。
 */
@Tag(name = "商品浏览", description = "商品/分类/标签浏览接口（免登录）")
@RestController
@RequestMapping("/api")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "商品列表")
    @GetMapping("/products")
    public Result<List<ProductVO>> listProducts() {
        return Result.success(productService.listProducts());
    }

}