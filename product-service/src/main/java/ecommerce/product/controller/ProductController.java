package ecommerce.product.controller;

import ecommerce.common.PageVO;
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

import java.math.BigDecimal;
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

    @Operation(summary = "商品列表（可选 categoryId 按一级分类过滤 / keyword 关键词搜索）")
    @GetMapping("/products")
    public Result<List<ProductVO>> listProducts(@RequestParam(required = false) Long categoryId,
                                                @RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            return Result.success(productService.searchProducts(keyword));
        }
        return Result.success(categoryId == null
                ? productService.listProducts()
                : productService.listProductsByCategory(categoryId));
    }

    @Operation(summary = "随机商品（首页下拉刷新换一批，excludeIds 排除当前已展示的）")
    @GetMapping("/products/random")
    public Result<List<ProductVO>> randomProducts(@RequestParam(defaultValue = "10") Integer limit,
                                                  @RequestParam(required = false) List<Long> excludeIds) {
        return Result.success(productService.listRandomProducts(limit, excludeIds));
    }

    @Operation(summary = "热门推荐（推荐分类优先，不够随机补足；excludeIds 排除当前已展示的）")
    @GetMapping("/products/hot")
    public Result<List<ProductVO>> hotProducts(@RequestParam(defaultValue = "8") Integer limit,
                                               @RequestParam(required = false) List<Long> excludeIds) {
        return Result.success(productService.listHotProducts(limit, excludeIds));
    }

    @Operation(summary = "商品分页列表（分类/关键词/品牌/价格区间/排序）")
    @GetMapping("/products/page")
    public Result<PageVO<ProductVO>> pageProducts(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "12") int pageSize,
                                                  @RequestParam(required = false) Long categoryId,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String brand,
                                                  @RequestParam(required = false) BigDecimal minPrice,
                                                  @RequestParam(required = false) BigDecimal maxPrice,
                                                  @RequestParam(defaultValue = "default") String sort) {
        return Result.success(productService.pageProducts(
                page, pageSize, categoryId, keyword, brand, minPrice, maxPrice, sort));
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

    @Operation(summary = "商品标签（单个商品关联的标签，最多一条）")
    @GetMapping("/products/{id}/tags")
    public Result<List<TagVO>> listProductTags(@PathVariable Long id) {
        return Result.success(productService.listTagsByProduct(id));
    }

}