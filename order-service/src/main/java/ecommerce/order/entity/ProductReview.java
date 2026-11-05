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
 * <p>
 * 2026-11-02 后台评价管理上线，本实体补了 4 个字段：
 * <ul>
 *   <li>{@code reply} / {@code replyTime} —— 商家回复；一条评价只保留一条回复，再次回复覆盖</li>
 *   <li>{@code isVirtual} —— 1 虚拟评价（后台造的）/ 0 买家真实评价</li>
 *   <li>{@code createdBy} —— 虚拟评价创建人（后台管理员 admin.id，与 C 端 user.id 是两套 ID 空间）</li>
 * </ul>
 * 可见性复用已有的 {@code status}（1 显示 / 0 隐藏），逻辑删除复用 {@code isDeleted}，不另加 visible/del_flag 列。
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
    /** 多值标签 JSON 数组，最多 4 个 */
    private String tags;
    private String content;
    private String images;
    private Integer status;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 商家回复正文（一条评价只保留一条回复，再次回复覆盖） */
    private String reply;

    /** 商家回复时间（Java 传参，不用 MySQL NOW()） */
    private LocalDateTime replyTime;

    /** 是否虚拟评价：1 虚拟（后台造的）0 买家真实 */
    private Integer isVirtual;

    /** 虚拟评价创建人（后台管理员 admin.id） */
    private Long createdBy;
}
