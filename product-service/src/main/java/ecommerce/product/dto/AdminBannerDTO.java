package ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 后台 Banner 表单入参（新增 / 编辑共用）。
 */
@Data
public class AdminBannerDTO {

    @Schema(description = "标题")
    private String title;

    @Schema(description = "图片地址列表，1~4 张，首张作为封面")
    private List<String> images;

    @Schema(description = "广告位置：1 小程序首页 2 小程序分类页 11 PC商城首页")
    private Integer position;

    @Schema(description = "跳转类型：0 不跳转 1 商品 2 文章 3 外部链接")
    private Integer linkType;

    @Schema(description = "跳转目标：商品ID / 文章ID / 外链URL")
    private String linkValue;

    @Schema(description = "排序，越小越靠前")
    private Integer sortOrder;

    @Schema(description = "状态：1 启用 0 禁用")
    private Integer status;
}
