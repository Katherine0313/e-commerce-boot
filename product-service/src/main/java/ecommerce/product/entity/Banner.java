package ecommerce.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Banner 广告位实体。
 * <p>
 * 一条记录服务一个展示位置，最多配置 4 张图：images 存 JSON 数组（完整列表），
 * image 存首图，兼容只认单图的旧客户端。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Banner {
    private Long id;
    /** 标题 */
    private String title;
    /** 首图地址（相对路径，如 /image/banner/xxx.webp） */
    private String image;
    /** 图片地址 JSON 数组（最多 4 张，首张与 image 相同） */
    private String images;
    /** 广告位置：1 小程序首页 / 2 小程序分类页 / 11 PC商城首页 */
    private Integer position;
    /** 跳转类型：0 不跳转 / 1 商品 / 2 文章 / 3 外部链接 */
    private Integer linkType;
    /** 跳转目标：商品ID / 文章ID / 外链URL */
    private String linkValue;
    /** 排序，越小越靠前 */
    private Integer sortOrder;
    /** 状态：1 启用 / 0 禁用 */
    private Integer status;
    /** 点击次数 */
    private Integer browseCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
