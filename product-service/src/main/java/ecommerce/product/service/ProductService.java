package ecommerce.product.service;

import ecommerce.product.vo.CategoryVO;
import ecommerce.product.vo.ProductVO;
import ecommerce.product.vo.TagVO;

import java.util.List;

public interface ProductService {

    /**
     * 商品列表（占位：返回固定示例数据，接库后改为查 products 表）
     */
    List<ProductVO> listProducts();

    /**
     * 商品详情；商品不存在时抛 404
     */
    ProductVO getProduct(Long id);

    /**
     * 分类列表（parentId 构成分类树）
     */
    List<CategoryVO> listCategories();

    /**
     * 标签列表
     */
    List<TagVO> listTags();
}
