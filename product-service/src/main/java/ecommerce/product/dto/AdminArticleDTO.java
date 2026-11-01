package ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 后台文章表单入参（新增 / 编辑共用）。
 */
@Data
public class AdminArticleDTO {

    @Schema(description = "文章分类 ID，0 表示未分类")
    private Long categoryId;

    @Schema(description = "标题（唯一）")
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

    @Schema(description = "是否热门（小程序推荐位）")
    private Integer recommendHot;

    @Schema(description = "是否轮播（小程序文章轮播位）")
    private Integer recommendBanner;

    @Schema(description = "关联商品 ID，0 表示不关联")
    private Long spuId;
}
