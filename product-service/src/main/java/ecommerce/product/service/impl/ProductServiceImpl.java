package ecommerce.product.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.product.entity.Category;
import ecommerce.product.entity.Product;
import ecommerce.product.entity.Tag;
import ecommerce.product.mapper.CategoryMapper;
import ecommerce.product.mapper.ProductMapper;
import ecommerce.product.mapper.TagMapper;
import ecommerce.product.service.ProductService;
import ecommerce.product.vo.CategoryVO;
import ecommerce.product.vo.ProductVO;
import ecommerce.product.vo.TagVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品浏览 Service。
 * <p>
 * 小程序首页的商品来自模板店（shopId=1）。多租户沙箱（按 token 解析 shopId）后续接入，
 * 届时把 TEMPLATE_SHOP_ID 换成从上下文取真实 shopId 即可，mapper 查询已按 shopId 参数化。
 */
@Service
public class ProductServiceImpl implements ProductService {

    /** 模板店（杰米小店）：小程序首页展示的数据源，多租户沙箱接入前固定为 1 */
    private static final Long TEMPLATE_SHOP_ID = 1L;

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;

    public ProductServiceImpl(ProductMapper productMapper, CategoryMapper categoryMapper, TagMapper tagMapper) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
    }

    @Override
    public List<ProductVO> listProducts() {
        return productMapper.listOnSale(TEMPLATE_SHOP_ID).stream()
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
    public List<CategoryVO> listCategories() {
        return categoryMapper.listCategories(TEMPLATE_SHOP_ID).stream()
                .map(this::toVO)
                .toList();
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

    private CategoryVO toVO(Category c) {
        return CategoryVO.builder()
                .id(c.getId())
                .name(c.getName())
                .parentId(c.getParentId())
                .sort(c.getSortOrder())
                .status(c.getStatus())
                .recommend(c.getRecommend())
                .productCount(c.getProductCount())
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
