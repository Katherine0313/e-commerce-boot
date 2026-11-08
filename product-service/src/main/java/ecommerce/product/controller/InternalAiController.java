package ecommerce.product.controller;

import ecommerce.common.PageVO;
import ecommerce.common.Result;
import ecommerce.exception.BusinessException;
import ecommerce.product.dto.AdminProductDTO;
import ecommerce.product.entity.Category;
import ecommerce.product.entity.ProductSku;
import ecommerce.product.mapper.CategoryMapper;
import ecommerce.product.mapper.ProductMapper;
import ecommerce.product.mapper.ProductSkuMapper;
import ecommerce.product.service.AdminProductService;
import ecommerce.product.service.ProductService;
import ecommerce.product.vo.ProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AI 内部接口（/internal/ai/**）：Python AI 服务（FastAPI）内网调用，取商品数据 / 回填文案。
 * <p>
 * 鉴权靠 common 的 InternalApiInterceptor（X-Internal-Token 共享密钥），不加权限注解。
 * <p>
 * 安全约束：商品数据面向 C 端 AI 客服，绝不下发 costPrice（成本价）——
 * 这里取数走 C 端 ProductService / findById，SQL 本身就不查 cost_price。
 */
@Tag(name = "AI 内部接口", description = "Python AI 服务内部调用（X-Internal-Token 鉴权）")
@RestController
@RequestMapping("/internal/ai")
public class InternalAiController {

    private final ProductService productService;
    private final AdminProductService adminProductService;
    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final CategoryMapper categoryMapper;

    public InternalAiController(ProductService productService, AdminProductService adminProductService,
                                ProductMapper productMapper, ProductSkuMapper skuMapper,
                                CategoryMapper categoryMapper) {
        this.productService = productService;
        this.adminProductService = adminProductService;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.categoryMapper = categoryMapper;
    }

    @Operation(summary = "商品详情（仅在售；不含成本价）")
    @GetMapping("/product/{id}")
    public Result<ProductDetailVO> getProduct(@PathVariable Long id) {
        ProductVO vo;
        try {
            vo = productService.getProduct(id);
        } catch (BusinessException e) {
            // 统一成内部接口的 404 口径：不存在与已下架对外不区分
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在或已下架");
        }
        if (vo.getStatus() == null || vo.getStatus() != 1) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在或已下架");
        }
        return Result.success(ProductDetailVO.builder()
                .id(vo.getId())
                .name(vo.getName())
                .subTitle(vo.getSubTitle())
                .price(vo.getPrice())
                .originalPrice(vo.getMarketPrice())
                .mainImage(vo.getImage())
                .sales(productMapper.sumSalesByProductId(id))
                .stock(vo.getStock())
                .categoryName(categoryName(vo.getCategoryId()))
                .description(vo.getBody())
                .build());
    }

    @Operation(summary = "商品搜索（仅在售；关键词/分类/价格区间/排序）")
    @GetMapping("/products")
    public Result<ProductPageVO> searchProducts(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Long categoryId,
                                                @RequestParam(required = false) BigDecimal minPrice,
                                                @RequestParam(required = false) BigDecimal maxPrice,
                                                @RequestParam(defaultValue = "comprehensive") String orderBy,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(20, Math.max(1, size));
        // orderBy 映射 C 端排序白名单：comprehensive→default、new→newest
        String sort = switch (orderBy == null ? "comprehensive" : orderBy) {
            case "new" -> "newest";
            case "price_asc", "price_desc" -> orderBy;
            default -> "default";
        };
        PageVO<ProductVO> result = productService.pageProducts(
                safePage, safeSize, categoryId, keyword, null, minPrice, maxPrice, sort);
        List<ProductVO> list = result.getList();
        Map<Long, Long> salesMap = salesByProductIds(
                list.stream().map(ProductVO::getId).filter(Objects::nonNull).toList());
        Map<Long, String> categoryNames = categoryNames(
                list.stream().map(ProductVO::getCategoryId).filter(Objects::nonNull).distinct().toList());
        List<ProductItemVO> records = list.stream()
                .map(p -> ProductItemVO.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .subTitle(p.getSubTitle())
                        .price(p.getPrice())
                        .originalPrice(p.getMarketPrice())
                        .mainImage(p.getImage())
                        .sales(salesMap.getOrDefault(p.getId(), 0L))
                        .stock(p.getStock())
                        .categoryName(p.getCategoryId() == null ? null : categoryNames.get(p.getCategoryId()))
                        .build())
                .toList();
        return Result.success(new ProductPageVO(records, result.getTotal()));
    }

    @Operation(summary = "更新商品文案（白名单字段：name / subTitle / description，其余忽略）")
    @PutMapping("/product/{id}")
    public Result<Map<String, Object>> updateProduct(@PathVariable Long id,
                                                     @RequestBody CopyUpdateRequest body) {
        // 局部更新语义：DTO 只设白名单字段，其余留 null 由 Service 跳过；
        // description 对应 products.body（图文详情），与文案生成场景 DESCRIPTION→body 的约定一致
        AdminProductDTO dto = new AdminProductDTO();
        if (body != null) {
            dto.setName(body.getName());
            dto.setSubTitle(body.getSubTitle());
            dto.setBody(body.getDescription());
        }
        adminProductService.update(id, dto);
        return Result.success(Map.of("id", id));
    }

    @Operation(summary = "SKU 库存（含累计销量）")
    @GetMapping("/stock/{skuId}")
    public Result<SkuStockVO> getStock(@PathVariable Long skuId) {
        ProductSku sku = skuMapper.findById(skuId);
        if (sku == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "SKU 不存在");
        }
        return Result.success(SkuStockVO.builder()
                .skuId(sku.getId())
                .productId(sku.getProductId())
                .stock(sku.getStock())
                .sales(skuMapper.sumSalesBySkuId(skuId))
                .build());
    }

    /** 单个分类名（无分类或分类已删返回 null） */
    private String categoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        Category category = categoryMapper.findById(categoryId);
        return category == null ? null : category.getName();
    }

    /** 批量商品销量：无商品时直接空 map，避免拼空 IN 查询 */
    private Map<Long, Long> salesByProductIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return productMapper.sumSalesByProductIds(ids).stream()
                .collect(Collectors.toMap(row -> ((Number) row.get("productId")).longValue(),
                        row -> ((Number) row.get("sales")).longValue()));
    }

    /** 批量分类名：id → name */
    private Map<Long, String> categoryNames(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return categoryMapper.listByIds(ids).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
    }

    /** AI 商品详情（不含成本价） */
    @Data
    @Builder
    public static class ProductDetailVO {
        private Long id;
        private String name;
        private String subTitle;
        /** 售价（有活动时为活动价） */
        private BigDecimal price;
        /** 市场价/划线价 */
        private BigDecimal originalPrice;
        private String mainImage;
        /** 累计销量（已支付订单件数） */
        private Long sales;
        private Integer stock;
        private String categoryName;
        /** 图文详情（products.body） */
        private String description;
    }

    /** AI 商品列表项（不含图文详情大字段） */
    @Data
    @Builder
    public static class ProductItemVO {
        private Long id;
        private String name;
        private String subTitle;
        private BigDecimal price;
        private BigDecimal originalPrice;
        private String mainImage;
        private Long sales;
        private Integer stock;
        private String categoryName;
    }

    /** AI 商品分页结果（records + total，与 Python 侧约定一致） */
    @Data
    @AllArgsConstructor
    public static class ProductPageVO {
        private List<ProductItemVO> records;
        private long total;
    }

    /** 文案更新请求体：白名单三字段，其余字段直接忽略 */
    @Data
    public static class CopyUpdateRequest {
        private String name;
        private String subTitle;
        private String description;
    }

    /** SKU 库存视图 */
    @Data
    @Builder
    public static class SkuStockVO {
        private Long skuId;
        private Long productId;
        private Integer stock;
        private Long sales;
    }
}
