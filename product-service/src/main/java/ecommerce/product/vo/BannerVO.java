package ecommerce.product.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Banner VO（后台列表 / 详情 / C 端轮播共用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BannerVO {
    @Schema(description = "Banner ID")
    private Long id;
    @Schema(description = "标题")
    private String title;
    @Schema(description = "首图地址（相对路径）")
    private String image;
    @Schema(description = "图片地址列表（最多 4 张，首张与 image 相同）")
    private List<String> images;
    @Schema(description = "广告位置：1 小程序首页 2 小程序分类页 11 PC商城首页")
    private Integer position;
    @Schema(description = "广告位置中文")
    private String positionDesc;
    @Schema(description = "跳转类型：0 不跳转 1 商品 2 文章 3 外部链接")
    private Integer linkType;
    @Schema(description = "跳转目标")
    private String linkValue;
    @Schema(description = "排序，越小越靠前")
    private Integer sortOrder;
    @Schema(description = "状态：1 启用 0 禁用")
    private Integer status;
    @Schema(description = "状态中文")
    private String statusDesc;
    @Schema(description = "点击次数")
    private Integer browseCount;
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
