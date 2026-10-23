package ecommerce.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商品收藏实体。
 * <p>
 * 账号级数据：同一用户在同一店铺内对同一商品只有一条记录（uk_shop_user_product）。
 * 取消收藏直接物理删除，收藏不是业务单据，不需要软删。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFavorite {
    private Long id;
    private Long shopId;
    private Long userId;
    private Long productId;
    private LocalDateTime createdAt;
}
