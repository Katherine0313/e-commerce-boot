package ecommerce.product.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.product.service.ProductService;
import ecommerce.product.vo.CategoryVO;
import ecommerce.product.vo.ProductVO;
import ecommerce.product.vo.TagVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品浏览 Service（占位骨架）。
 * <p>
 * 表结构尚未设计、未接数据库，这里返回固定示例数据，先把「网关路由 + 免登录 + 接口文档」这条链路跑通；
 * 后续阶段 2/3 设计 products/categories/tags 表并接 MyBatis 后，替换为真实查询。
 */
@Service
public class ProductServiceImpl implements ProductService {

    /** 占位商品数据，供前端联调 */
    private static final List<ProductVO> DEMO_PRODUCTS = List.of(
            ProductVO.builder()
                    .id(1L).name("示例商品 · 新疆阿克苏苹果")
                    .price(new BigDecimal("19.90")).stock(100)
                    .image("/upload/product/demo-apple.png")
                    .description("脆甜多汁，产地直发（占位数据，接库后替换）。")
                    .categoryId(1L).status(1)
                    .build(),
            ProductVO.builder()
                    .id(2L).name("示例商品 · 精选赣南脐橙")
                    .price(new BigDecimal("29.90")).stock(50)
                    .image("/upload/product/demo-orange.png")
                    .description("果肉饱满，酸甜适中（占位数据，接库后替换）。")
                    .categoryId(1L).status(1)
                    .build()
    );

    /** 占位分类数据，parentId 构成分类树（顶级分类 parentId=null） */
    private static final List<CategoryVO> DEMO_CATEGORIES = List.of(
            CategoryVO.builder().id(1L).name("水果").parentId(null).sort(1).build(),
            CategoryVO.builder().id(2L).name("蔬菜").parentId(null).sort(2).build(),
            CategoryVO.builder().id(3L).name("苹果").parentId(1L).sort(1).build()
    );

    /** 占位标签数据 */
    private static final List<TagVO> DEMO_TAGS = List.of(
            TagVO.builder().id(1L).name("热销").build(),
            TagVO.builder().id(2L).name("新品").build(),
            TagVO.builder().id(3L).name("包邮").build()
    );

    @Override
    public List<ProductVO> listProducts() {
        return DEMO_PRODUCTS;
    }

    @Override
    public ProductVO getProduct(Long id) {
        // 占位：从示例数据里按 id 找，找不到返回 404（与真实查库后行为一致）
        return DEMO_PRODUCTS.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "商品不存在"));
    }

    @Override
    public List<CategoryVO> listCategories() {
        return DEMO_CATEGORIES;
    }

    @Override
    public List<TagVO> listTags() {
        return DEMO_TAGS;
    }
}
