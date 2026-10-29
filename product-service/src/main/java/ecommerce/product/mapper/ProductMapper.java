package ecommerce.product.mapper;

import ecommerce.product.entity.Product;
import ecommerce.product.vo.DashboardVO;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ProductMapper {
    // 上架商品列表（listing_status=1，不含图文详情 body 与成本价 cost_price）
    List<Product> listOnSale();

    // 根据 id 查单个商品（含图文详情 body，仍不下发 cost_price）
    Product findById(@Param("id") Long id);

    // 按 id 列表查上架商品（推荐接口用，不含 body/cost_price）
    List<Product> listOnSaleByIds(@Param("ids") List<Long> ids);

    // 按一级分类（父类）查上架商品：商品挂在二级分类，按 parent_id 聚合到父类（不含 body/cost_price）
    List<Product> listOnSaleByCategory(@Param("categoryId") Long categoryId);

    // 按关键词搜索上架商品：name/sub_title/brand 模糊匹配（不含 body/cost_price）
    List<Product> searchOnSale(@Param("keyword") String keyword);

    // 随机取上架商品（首页下拉刷新「换一批」用）；excludeIds 排除当前已展示的，避免刷新后大量重复
    List<Product> listRandomOnSale(@Param("limit") int limit,
                                   @Param("excludeIds") List<Long> excludeIds);

    // 随机取「推荐分类」下的上架商品：分类自身或父分类 recommend=1（首页热门推荐优先用）
    List<Product> listRandomOnSaleByRecommendedCategory(@Param("limit") int limit,
                                                        @Param("excludeIds") List<Long> excludeIds);

    // C 端分页商品列表：分类支持一级/二级、关键词、品牌、价格区间、排序白名单
    List<Product> pageOnSale(@Param("categoryId") Long categoryId, @Param("keyword") String keyword,
                             @Param("brand") String brand, @Param("minPrice") BigDecimal minPrice,
                             @Param("maxPrice") BigDecimal maxPrice, @Param("sort") String sort,
                             @Param("offset") int offset, @Param("pageSize") int pageSize);

    // C 端分页总数：筛选条件与 pageOnSale 一致
    long countOnSale(@Param("categoryId") Long categoryId, @Param("keyword") String keyword,
                     @Param("brand") String brand, @Param("minPrice") BigDecimal minPrice,
                     @Param("maxPrice") BigDecimal maxPrice);

    // ==================== 后台管理（/api/admin/**） ====================

    // 后台商品列表：含下架、含成本价与图文详情（编辑回填需要）；分页 + 可选关键词/分类筛选
    List<Product> listAdmin(@Param("search") String search, @Param("categoryId") Long categoryId,
                            @Param("offset") int offset, @Param("pageSize") int pageSize);

    // 后台商品总数（与 listAdmin 同筛选条件，分页 total 用）
    long countAdmin(@Param("search") String search, @Param("categoryId") Long categoryId);

    // 后台商品详情（含成本价 cost_price）
    Product findByIdAdmin(@Param("id") Long id);

    // 新增商品，自增 id 回填到 product.id
    int insert(Product product);

    // 更新商品（动态 set，只更新非 null 字段，支持局部更新如上架开关）
    int update(Product product);

    // 删除商品
    int delete(@Param("id") Long id);

    // 品牌列表（去重、非空）
    List<String> listBrands();

    // 工作台统计：商品总数 / 总库存 / 库存总价值（售价 × 库存）
    DashboardVO dashboardStats();
}