package ecommerce.product.mapper;

import ecommerce.product.entity.Product;
import ecommerce.product.entity.Tag;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TagMapper {
    // 标签列表（product_count 读物化列，写操作维护）
    List<Tag> listTags();

    // 根据 id 查单个标签
    Tag findById(@Param("id") Long id);

    // 按 code 查标签（唯一校验用）
    Tag findByCode(@Param("code") String code);

    // 新增标签，自增 id 回填
    int insert(Tag tag);

    // 更新标签（动态 set，只更新非 null 字段）
    int update(Tag tag);

    // 删除标签（products.tag_id 外键 ON DELETE SET NULL 自动解绑商品）
    int delete(@Param("id") Long id);

    // 标签关联的商品列表（products.tag_id = tagId）
    List<Product> listProductsByTag(@Param("tagId") Long tagId);

    // 根据商品查标签（当前一商品最多一个标签，返回列表便于后续扩展）
    List<Tag> listTagsByProductId(@Param("productId") Long productId);

    // 给一批商品打上标签（更新 products.tag_id）
    int bindProductsToTag(@Param("tagId") Long tagId, @Param("productIds") List<Long> productIds,
                          @Param("now") LocalDateTime now);

    // 解绑单个商品的标签（products.tag_id = NULL，仅当当前标签匹配）
    int unbindProductTag(@Param("productId") Long productId, @Param("tagId") Long tagId,
                         @Param("now") LocalDateTime now);

    // 批量解绑标签下的商品（products.tag_id = NULL）
    int unbindTagProducts(@Param("tagId") Long tagId, @Param("productIds") List<Long> productIds,
                          @Param("now") LocalDateTime now);

    // 重算每个标签的 product_count（基于 products.tag_id）
    void refreshProductCounts();
}
