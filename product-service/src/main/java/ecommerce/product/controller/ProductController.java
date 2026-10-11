package ecommerce.product.controller;

import ecommerce.common.Result;
import ecommerce.product.service.ProductService;
import ecommerce.product.vo.CategoryTreeVO;
import ecommerce.product.vo.ProductVO;
import ecommerce.product.vo.TagVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @Operation(summary = "商品列表（可选 categoryId 按一级分类过滤）")
    @GetMapping("/products")
    public Result<List<ProductVO>> listProducts(@RequestParam(required = false) Long categoryId) {
        return Result.success(categoryId == null
                ? productService.listProducts()
                : productService.listProductsByCategory(categoryId));
    }

    @Operation(summary = "商品详情")
    @GetMapping("/products/{id}")
    public Result<ProductVO> getProduct(@PathVariable Long id) {

        return Result.success(productService.getProduct(id));
    }

    @Operation(summary = "分类列表（树形：一级父分类含 children，小程序首页分类入口）")
    @GetMapping("/categories")
    public Result<List<CategoryTreeVO>> listCategories() {

        return Result.success(productService.listCategories());
    }

    @Operation(summary = "标签列表")
    @GetMapping("/tags")
    public Result<List<TagVO>> listTags() {
        return Result.success(productService.listTags());
    }

}