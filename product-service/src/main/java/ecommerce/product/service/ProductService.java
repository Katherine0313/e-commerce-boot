package ecommerce.product.service;

import ecommerce.product.vo.CategoryTreeVO;
import ecommerce.product.vo.ProductVO;
import ecommerce.product.vo.TagVO;

import java.util.List;

public interface ProductService {

    /**
     * 商品列表（占位：返回固定示例数据，接库后改为查 products 表）
     */
    List<ProductVO> listProducts();

    /**
     * 按一级分类（父类）查上架商品：聚合该父类名下所有二级分类的商品
     */
    List<ProductVO> listProductsByCategory(Long categoryId);

    /**
     * 按关键词搜索上架商品：匹配商品名 / 副标题 / 品牌
     */
    List<ProductVO> searchProducts(String keyword);

    /**
     * 随机取上架商品：首页下拉刷新「换一批」用。excludeIds 排除当前已展示的商品，
     * 避免刷新后和上一批大量重复；排除后不足则返回实际数量。
     */
    List<ProductVO> listRandomProducts(int limit, List<Long> excludeIds);

    /**
     * 首页热门推荐：优先取「推荐分类」（categories.recommend=1，含父分类推荐）下的商品，
     * 不够 limit 时用随机上架商品补足；excludeIds 排除当前已展示的，下拉刷新换一批用。
     */
    List<ProductVO> listHotProducts(int limit, List<Long> excludeIds);

    /**
     * 商品详情；商品不存在时抛 404
     */
    ProductVO getProduct(Long id);

    /**
     * 按 id 列表批量查上架商品，仅供 RecommendServiceImpl 内部调用，不是对外 HTTP 接口。
     * <p>
     * 用途：推荐服务（Python）返回「猜你喜欢」的一批商品 id 后，用它一次查出商品拼 VO，
     * 避免循环单查产生 N+1 条 SQL。id 不存在或已下架时自动过滤；返回顺序不保证与入参一致，
     * 由调用方（RecommendServiceImpl）按 id 顺序自行重排。
     */
    List<ProductVO> listProductsByIds(List<Long> ids);

    /**
     * 分类树列表：每个元素是一级父分类（含 children 子分类）
     */
    List<CategoryTreeVO> listCategories();

    /**
     * 标签列表
     */
    List<TagVO> listTags();
}