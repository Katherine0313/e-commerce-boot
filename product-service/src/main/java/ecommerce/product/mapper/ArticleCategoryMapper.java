package ecommerce.product.mapper;

import ecommerce.product.entity.ArticleCategory;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章分类 Mapper。
 */
public interface ArticleCategoryMapper {

    // 全部分类（带文章数）
    List<ArticleCategory> listAll();

    // 按 id 查单条
    ArticleCategory findById(@Param("id") Long id);

    // 新增
    int insert(ArticleCategory category);

    // 更新（动态 set，只更新非 null 字段）
    int update(ArticleCategory category);

    // 逻辑删除
    int softDelete(@Param("id") Long id, @Param("updateTime") LocalDateTime updateTime);
}
