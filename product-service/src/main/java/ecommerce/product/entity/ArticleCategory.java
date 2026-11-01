package ecommerce.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章分类实体（只在后台归类用，C 端不展示）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleCategory {
    private Long id;
    /** 分类名称 */
    private String name;
    /** 分类图标（可选） */
    private String picUrl;
    /** 排序，越小越靠前 */
    private Integer sortOrder;
    /** 状态：1 启用 / 0 禁用 */
    private Integer status;
    /** 逻辑删除：0 正常 / 1 已删除 */
    private Integer isDeleted;
    /** 该分类下的文章数：不是落库列，由查询时的子查询实时算出 */
    private Integer articleCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
