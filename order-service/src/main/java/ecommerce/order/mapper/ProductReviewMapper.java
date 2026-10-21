package ecommerce.order.mapper;

import ecommerce.order.entity.ProductReview;
import org.apache.ibatis.annotations.Param;

/**
 * 商品评价 Mapper（product_reviews 表）。
 */
public interface ProductReviewMapper {

    // 插入评价（回填自增 id）
    int insert(ProductReview review);

    // 查某订单的评价（不含软删除）；用于「是否已评价」标记与防重复提交
    ProductReview findByOrderId(@Param("orderId") Long orderId, @Param("shopId") Long shopId);
}
