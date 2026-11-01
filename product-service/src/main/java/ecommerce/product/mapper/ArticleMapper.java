package ecommerce.product.mapper;

import ecommerce.product.entity.Article;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章 Mapper。
 */
public interface ArticleMapper {

    // 后台列表（分页 + 标题/分类/状态筛选）
    List<Article> listAdmin(@Param("keyword") String keyword,
                             @Param("categoryId") Long categoryId,
                             @Param("status") Integer status,
                             @Param("offset") int offset,
                             @Param("pageSize") int pageSize);

    // 后台列表总条数
    long countAdmin(@Param("keyword") String keyword,
                    @Param("categoryId") Long categoryId,
                    @Param("status") Integer status);

    // 按 id 查单条
    Article findById(@Param("id") Long id);

    // 按 id 查启用中的单条（C 端详情用）
    Article findEnabledById(@Param("id") Long id);

    // 按标题查单条（C 端富文本页用；只查启用且未删除的）
    Article findEnabledByTitle(@Param("title") String title);

    // C 端：查启用的文章（可按热门/轮播过滤，limit 为空则不限）
    List<Article> listEnabled(@Param("recommendHot") Integer recommendHot,
                              @Param("recommendBanner") Integer recommendBanner,
                              @Param("limit") Integer limit);

    // 标题查重：查全量（含已逻辑删除的记录，删除后标题仍占用）
    long countByTitle(@Param("title") String title, @Param("excludeId") Long excludeId);

    // 标题查重：只查未删除的（区分「已存在」和「被已删除文章占用」两种提示）
    long countAliveByTitle(@Param("title") String title, @Param("excludeId") Long excludeId);

    // 统计某个分类下的文章数（分类删除前校验用）
    long countByCategoryId(@Param("categoryId") Long categoryId);

    // 新增
    int insert(Article article);

    // 更新（动态 set，只更新非 null 字段）
    int update(Article article);

    // 更新状态（启用/禁用）
    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("updateTime") LocalDateTime updateTime);

    // 逻辑删除
    int softDelete(@Param("id") Long id, @Param("updateTime") LocalDateTime updateTime);

    // 浏览量 +1
    int increaseBrowseCount(@Param("id") Long id);
}
