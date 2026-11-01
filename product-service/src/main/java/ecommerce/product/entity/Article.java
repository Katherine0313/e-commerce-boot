package ecommerce.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章实体（关于我们 / 隐私政策 / 店铺声明 / 常见问题这类固定图文）。
 * <p>
 * title 唯一（含逻辑删除的记录）：小程序按标题查详情，标题重复会查错。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Article {
    private Long id;
    /** 文章分类 ID，0 表示未分类 */
    private Long categoryId;
    /** 分类名称：不是落库列，由查询时 join 分类表带出 */
    private String categoryName;
    /** 标题 */
    private String title;
    /** 作者 */
    private String author;
    /** 封面图地址 */
    private String cover;
    /** 正文 HTML */
    private String content;
    /** 简介 */
    private String introduction;
    /** 排序，越小越靠前 */
    private Integer sortOrder;
    /** 状态：1 启用 / 0 禁用 */
    private Integer status;
    /** 是否热门（小程序推荐位） */
    private Integer recommendHot;
    /** 是否轮播（小程序文章轮播位） */
    private Integer recommendBanner;
    /** 浏览量 */
    private Integer browseCount;
    /** 关联商品 ID，0 表示不关联 */
    private Long spuId;
    /** 逻辑删除：0 正常 / 1 已删除 */
    private Integer isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
