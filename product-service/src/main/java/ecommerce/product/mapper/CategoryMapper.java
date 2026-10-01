package ecommerce.product.mapper;

import ecommerce.product.entity.Category;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CategoryMapper {
    // 分类列表（product_count 子查询实时算，不落库）
    List<Category> listCategories(@Param("shopId") Long shopId);
}
