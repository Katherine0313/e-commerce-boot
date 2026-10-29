package ecommerce.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单项实体（对应 order_items 表）。
 * <p>
 * name/image/sales_price 是下单时的商品快照，商品后续改价/改名不影响已下单的明细。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {
    private Long id;
    private Long orderId;
    private Long productId;
    private String productName;
    private String image;
    private BigDecimal salesPrice;
    private Integer quantity;
    private BigDecimal subTotal;
    private String insuranceDetail;
    private BigDecimal insuranceFee;
    private Integer isDeleted;
    private LocalDateTime createdAt;
}
