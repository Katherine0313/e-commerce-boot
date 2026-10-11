package ecommerce.product.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.product.entity.Category;
import ecommerce.product.entity.Product;
import ecommerce.product.entity.Tag;
import ecommerce.product.mapper.CategoryMapper;
import ecommerce.product.mapper.ProductMapper;
import ecommerce.product.mapper.TagMapper;
import ecommerce.product.service.ProductService;
import ecommerce.product.vo.CategoryTreeVO;
import ecommerce.product.vo.ProductVO;
import ecommerce.product.vo.TagVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 商品浏览 Service。
 * <p>
 * 小程序首页的商品来自模板店（shopId=1）。多租户沙箱（按 token 解析 shopId）后续接入，
 * 届时把 TEMPLATE_SHOP_ID 换成从上下文取真实 shopId 即可，mapper 查询已按 shopId 参数化。
 */
@Slf4j
@Service
public class ProductServiceImpl implements ProductService {

    /** 模板店（杰米小店）：小程序首页展示的数据源，多租户沙箱接入前固定为 1 */
    private static final Long TEMPLATE_SHOP_ID = 1L;

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

    public ProductServiceImpl(ProductMapper productMapper, CategoryMapper categoryMapper, TagMapper tagMapper,
                              StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    // 获取上架的全量商品：仅供推荐兜底用，不缓存（前端分类页已改懒加载，不再拉全量）
    @Override
    public List<ProductVO> listProducts() {
        return productMapper.listOnSale(TEMPLATE_SHOP_ID).stream()
                .map(this::toVO)
                .toList();
    }

    // 按一级分类（父类）查上架商品（不缓存）
    @Override
    public List<ProductVO> listProductsByCategory(Long categoryId) {
        return productMapper.listOnSaleByCategory(categoryId, TEMPLATE_SHOP_ID).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public ProductVO getProduct(Long id) {
        Product product = productMapper.findById(id, TEMPLATE_SHOP_ID);
        if (product == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        return toVO(product);
    }

    @Override
    public List<ProductVO> listProductsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return productMapper.listOnSaleByIds(ids, TEMPLATE_SHOP_ID).stream()
                .map(this::toVO)
                .toList();
    }

    // 分类树：读 ids 索引 → MGET 各父分类树 → 合并返回；缺失则整体重建
    @Override
    public List<CategoryTreeVO> listCategories() {
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
    }

    /** 索引/父分类树缺失时全量重建：写 ids + 每棵父分类树 */
    private List<CategoryTreeVO> rebuildCategoryCache() {
        log.info("部分缓存缺失，现在全部重建：");
        List<Category> categories = categoryMapper.listCategories(TEMPLATE_SHOP_ID);
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
        return tagMapper.listTags(TEMPLATE_SHOP_ID).stream()
                .map(this::toVO)
                .toList();
    }

    private ProductVO toVO(Product p) {
        return ProductVO.builder()
                .id(p.getId())
                .name(p.getName())
                .subTitle(p.getSubTitle())
                .brand(p.getBrand())
                .image(p.getImage())
                .description(p.getDescription())
                .body(p.getBody())
                .price(p.getSalesPrice())
                .marketPrice(p.getMarketPrice())
                .stock(p.getStock())
                .categoryId(p.getCategoryId())
                .tagId(p.getTagId())
                .salesMode(p.getSalesMode())
                .status(p.getListingStatus())
                .productCode(p.getProductCode())
                .weight(p.getWeight())
                .volume(p.getVolume())
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
