package ecommerce.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Banner 广告位实体。
 * <p>
 * 一条记录只服务一个展示位置，终端由 position 决定，所以只存一张图（image）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Banner {
    private Long id;
    /** 标题 */
    private String title;
    /** 图片地址（相对路径，如 /image/banner/xxx.webp） */
    private String image;
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
    /** 逻辑删除：0 正常 / 1 已删除 */
    private Integer isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
