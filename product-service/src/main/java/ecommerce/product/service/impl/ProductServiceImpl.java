package ecommerce.product.service.impl;

import ecommerce.common.PageVO;
import ecommerce.common.insurance.InsurancePolicy;
import ecommerce.exception.BusinessException;
import ecommerce.product.entity.Category;
import ecommerce.product.entity.Product;
import ecommerce.product.entity.Tag;
import ecommerce.product.mapper.CategoryMapper;
import ecommerce.product.mapper.ProductMapper;
import ecommerce.product.mapper.TagMapper;
import ecommerce.product.service.ProductService;
import ecommerce.product.service.PromotionService;
import ecommerce.product.support.ProductSpecsCodec;
import ecommerce.product.vo.CategoryTreeVO;
import ecommerce.product.vo.ProductVO;
import ecommerce.product.vo.TagVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 商品浏览 Service（单店模式，不再区分店铺）。
 */
@Slf4j
@Service
public class ProductServiceImpl implements ProductService {

    /** 一级父分类 id 索引缓存 key，value 为 [父id...] 数组 */
    private static final String CATEGORY_PARENT_IDS_KEY = "category:parent:ids";
    /** 单个父分类树缓存 key 模板（%d = 父分类 id） */
    private static final String CATEGORY_PARENT_KEY_TEMPLATE = "category:parent:%d";
    /** 缓存过期时长：分类靠 SQL 导入、变更不频繁，TTL 到期自动回源刷新 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ProductSpecsCodec specsCodec;
    private final PromotionService promotionService;

    public ProductServiceImpl(ProductMapper productMapper, CategoryMapper categoryMapper, TagMapper tagMapper,
                              StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper,
                              ProductSpecsCodec specsCodec, PromotionService promotionService) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.specsCodec = specsCodec;
        this.promotionService = promotionService;
    }

    // 获取上架的全量商品：仅供推荐兜底用，不缓存（前端分类页已改懒加载，不再拉全量）
    @Override
    public List<ProductVO> listProducts() {
        return withPromotions(productMapper.listOnSale().stream()
                .map(this::toVO)
                .toList());
    }

    // 按一级分类（父类）查上架商品（不缓存）
    @Override
    public List<ProductVO> listProductsByCategory(Long categoryId) {
        return withPromotions(productMapper.listOnSaleByCategory(categoryId).stream()
                .map(this::toVO)
                .toList());
    }

    // 按关键词搜索上架商品（商品名/副标题/品牌模糊匹配）
    @Override
    public List<ProductVO> searchProducts(String keyword) {
        return withPromotions(productMapper.searchOnSale(keyword).stream()
                .map(this::toVO)
                .toList());
    }

    // C 端分页列表：参数归一化后交给 Mapper；旧 list/search 接口保持数组结构不变
    @Override
    public PageVO<ProductVO> pageProducts(int page, int pageSize, Long categoryId, String keyword,
                                          String brand, BigDecimal minPrice, BigDecimal maxPrice, String sort) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(60, Math.max(1, pageSize));
        String safeKeyword = normalize(keyword);
        String safeBrand = normalize(brand);
        if (minPrice != null && minPrice.signum() < 0 || maxPrice != null && maxPrice.signum() < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "价格不能为负数");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "最低价不能大于最高价");
        }
        String sortInput = sort == null ? "default" : sort;
        String safeSort = switch (sortInput) {
            case "price_asc", "price_desc", "newest", "default" -> sortInput;
            default -> "default";
        };
        long offsetLong = (long) (safePage - 1) * safePageSize;
        int offset = offsetLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) offsetLong;

        List<ProductVO> list = withPromotions(productMapper
                .pageOnSale(categoryId, safeKeyword, safeBrand, minPrice, maxPrice, safeSort, offset, safePageSize)
                .stream().map(this::toVO).toList());
        long total = productMapper.countOnSale(categoryId, safeKeyword, safeBrand, minPrice, maxPrice);
        return new PageVO<>(list, total, safePage, safePageSize);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // 随机取上架商品：首页下拉刷新换一批（排除当前已展示的 id）
    @Override
    public List<ProductVO> listRandomProducts(int limit, List<Long> excludeIds) {
        // 兜住异常入参，避免 LIMIT 负数/过大
        int size = Math.max(1, Math.min(limit, 50));
        return withPromotions(productMapper.listRandomOnSale(size, excludeIds).stream()
                .map(this::toVO)
                .toList());
    }

    // 首页热门推荐：推荐分类优先，不够再随机补足
    @Override
    public List<ProductVO> listHotProducts(int limit, List<Long> excludeIds) {
        int size = Math.max(1, Math.min(limit, 50));
        List<Long> exclude = excludeIds == null ? new ArrayList<>() : new ArrayList<>(excludeIds);

        List<ProductVO> hot = productMapper
                .listRandomOnSaleByRecommendedCategory(size, exclude).stream()
                .map(this::toVO)
                .toList();
        if (hot.size() >= size) {
            return withPromotions(hot);
        }

        // 推荐分类商品不够：排除已选中的 id 后再随机补足
        hot.forEach(p -> exclude.add(p.getId()));
        List<ProductVO> fill = productMapper
                .listRandomOnSale(size - hot.size(), exclude).stream()
                .map(this::toVO)
                .toList();
        List<ProductVO> result = new ArrayList<>(hot);
        result.addAll(fill);
        return withPromotions(result);
    }

    @Override
    public ProductVO getProduct(Long id) {
        Product product = productMapper.findById(id);
        if (product == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        ProductVO vo = toVO(product);
        promotionService.applyDisplay(List.of(vo));
        // 增值保障按最终展示价（含活动价）计价，规则由后端统一判断，前端只渲染返回的选项
        vo.setInsuranceOptions(InsurancePolicy.options(vo.getCategoryId(), vo.getPrice()));
        return vo;
    }

    @Override
    public List<ProductVO> listProductsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return withPromotions(productMapper.listOnSaleByIds(ids).stream()
                .map(this::toVO)
                .toList());
    }

    // 分类树：读 ids 索引 → MGET 各父分类树 → 合并返回；缺失则整体重建
    // Redis 不可用时降级直接查库：分类是首页/分类页的必需数据，不能因为缓存故障导致整页加载失败
    @Override
    public List<CategoryTreeVO> listCategories() {
        try {
            String idsJson = stringRedisTemplate.opsForValue().get(CATEGORY_PARENT_IDS_KEY);
            if (idsJson == null) {
                return rebuildCategoryCache();
            }
            List<Long> parentIds = readIds(idsJson);
            List<String> keys = parentIds.stream()
                    .map(id -> CATEGORY_PARENT_KEY_TEMPLATE.formatted(id))
                    .toList();
            List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
            // 有任一父分类树缺失就整体重建（分类少、变更不频繁，重建最省事）
            if (values.stream().anyMatch(Objects::isNull)) {
                return rebuildCategoryCache();
            }
            List<CategoryTreeVO> result = new ArrayList<>(values.size());
            for (String v : values) {
                result.add(readTree(v));
            }
            return result;
        } catch (RuntimeException e) {
            // 连接超时/拒绝、序列化失败等都回源查库，保证首页和分类页仍可用
            log.warn("分类缓存不可用，降级直接查库", e);
            return buildTree(categoryMapper.listCategories());
        }
    }

    /** 索引/父分类树缺失时全量重建：写 ids + 每棵父分类树 */
    private List<CategoryTreeVO> rebuildCategoryCache() {
        log.info("部分缓存缺失，现在全部重建：");
        List<Category> categories = categoryMapper.listCategories();
        List<CategoryTreeVO> tree = buildTree(categories);
        List<Long> parentIds = tree.stream().map(CategoryTreeVO::getId).collect(Collectors.toList());
        stringRedisTemplate.opsForValue().set(CATEGORY_PARENT_IDS_KEY, toJson(parentIds), CACHE_TTL);
        for (CategoryTreeVO parent : tree) {
            stringRedisTemplate.opsForValue().set(
                    CATEGORY_PARENT_KEY_TEMPLATE.formatted(parent.getId()), toJson(parent), CACHE_TTL);
        }
        return tree;
    }

    /** 把扁平分类列表组装成树：parent_id 为 null 的是一级父分类，其余挂到对应 children */
    private List<CategoryTreeVO> buildTree(List<Category> categories) {
        Map<Long, List<Category>> childrenMap = categories.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));
        List<CategoryTreeVO> parents = categories.stream()
                .filter(c -> c.getParentId() == null)
                .map(this::toTreeVO)
                .collect(Collectors.toList());
        for (CategoryTreeVO parent : parents) {
            List<Category> children = childrenMap.get(parent.getId());
            parent.setChildren(children == null ? List.of()
                    : children.stream().map(this::toTreeVO).collect(Collectors.toList()));
        }
        return parents;
    }

    private CategoryTreeVO toTreeVO(Category c) {
        return CategoryTreeVO.builder()
                .id(c.getId())
                .name(c.getName())
                .parentId(c.getParentId())
                .sort(c.getSortOrder())
                .status(c.getStatus())
                .recommend(c.getRecommend())
                .image(c.getImage())
                .productCount(c.getProductCount())
                .build();
    }

    /** 对象序列化为纯 JSON（无 @class） */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Redis 缓存序列化失败", e);
        }
    }

    private List<Long> readIds(String json) {
        try {
            return Arrays.asList(objectMapper.readValue(json, Long[].class));
        } catch (Exception e) {
            throw new RuntimeException("Redis 缓存反序列化失败", e);
        }
    }

    private CategoryTreeVO readTree(String json) {
        try {
            return objectMapper.readValue(json, CategoryTreeVO.class);
        } catch (Exception e) {
            throw new RuntimeException("Redis 缓存反序列化失败", e);
        }
    }

    @Override
    public List<TagVO> listTags() {
        return tagMapper.listTags().stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public List<TagVO> listTagsByProduct(Long productId) {
        // 商品不存在按详情接口口径返回 404，避免前端拿到空数组误判为「无标签」
        if (productMapper.findById(productId) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        return tagMapper.listTagsByProductId(productId).stream()
                .map(this::toVO)
                .toList();
    }

    /** 商品列表统一补促销价与标签（批量查活动，避免逐条查询） */
    private List<ProductVO> withPromotions(List<ProductVO> list) {
        promotionService.applyDisplay(list);
        return list;
    }

    private ProductVO toVO(Product p) {
        return ProductVO.builder()
                .id(p.getId())
                .name(p.getName())
                .subTitle(p.getSubTitle())
                .brand(p.getBrand())
                .image(p.getImage())
                .title(p.getTitle())
                .body(p.getBody())
                .price(p.getSalesPrice())
                .marketPrice(p.getMarketPrice())
                .stock(p.getStock())
                .categoryId(p.getCategoryId())
                .tagId(p.getTagId())
                .salesMode(p.getSalesMode())
                .status(p.getListingStatus())
                .productCode(p.getProductCode())
                .specs(specsCodec.decode(p.getSpecs()))
                .build();
    }

    private TagVO toVO(Tag t) {
        return TagVO.builder()
                .id(t.getId())
                .name(t.getName())
                .code(t.getCode())
                .type(t.getType())
                .color(t.getColor())
                .sort(t.getSortOrder())
                .status(t.getStatus())
                .productCount(t.getProductCount())
                .build();
    }
}
