package ecommerce.product.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章 VO（后台列表 / 详情 / C 端详情共用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleVO {
    @Schema(description = "文章 ID")
    private Long id;
    @Schema(description = "文章分类 ID")
    private Long categoryId;
    @Schema(description = "文章分类名称")
    private String categoryName;
    @Schema(description = "标题")
    private String title;
    @Schema(description = "作者")
    private String author;
    @Schema(description = "封面图地址")
    private String cover;
    @Schema(description = "正文 HTML")
    private String content;
    @Schema(description = "简介")
    private String introduction;
    @Schema(description = "排序，越小越靠前")
    private Integer sortOrder;
    @Schema(description = "状态：1 启用 0 禁用")
    private Integer status;
    @Schema(description = "状态中文")
    private String statusDesc;
    @Schema(description = "是否热门（小程序推荐位）")
    private Integer recommendHot;
    @Schema(description = "是否轮播（小程序文章轮播位）")
    private Integer recommendBanner;
    @Schema(description = "浏览量")
    private Integer browseCount;
    @Schema(description = "关联商品 ID")
    private Long spuId;
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
