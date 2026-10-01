package ecommerce.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商品分类实体。parentId 自引用构成分类树，顶级分类 parentId 为 null。
 * <p>
 * productCount 不是落库列，由查询时的子查询实时算出（只数直接挂在当前分类下的商品，不含子分类）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {
    private Long id;
    private Long shopId;
    private String name;
    private Long parentId;
    private Integer sortOrder;
    private Integer status;
    private Integer recommend;
    private Integer productCount;
}
