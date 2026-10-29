package ecommerce.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商品评价实体（对应 product_reviews 表）。
 * <p>
 * product_name/product_image 是下单商品的快照，商品日后删除时历史评价仍可读（不加外键）。
 * created_at 由 Java 传 LocalDateTime.now()（见 ReviewServiceImpl），不用 MySQL NOW() 避免 8 小时时区偏差。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductReview {
    private Long id;
    private Long orderId;
    private Long userId;
    private Long productId;
    private String productName;
    private String productImage;
    private Integer rating;
    private String tag;
    private String content;
    private String images;
    private Integer status;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
