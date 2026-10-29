package ecommerce.product.mapper;

import ecommerce.product.entity.Category;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CategoryMapper {
    // 分类列表（product_count 子查询实时算，不落库）
    List<Category> listCategories();

    // 根据 id 查单个分类（含 product_count 子查询）
    Category findById(@Param("id") Long id);

    // 新增分类（sort_order 由 service 计算）
    int insert(Category category);

    // 更新分类（动态 set，只更新非 null 字段；sort_order 不走这里，由 move 单独调整）
    int update(Category category);

    // 更新单个分类的排序值（move 重编号用）
    int updateSortOrder(@Param("id") Long id, @Param("sortOrder") Integer sortOrder);

    // 删除分类（products.category_id 外键 ON DELETE SET NULL 自动解绑商品）
    int delete(@Param("id") Long id);
}
