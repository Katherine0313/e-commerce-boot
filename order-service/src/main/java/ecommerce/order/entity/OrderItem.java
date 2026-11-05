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
    /** 原单价快照（有活动时用于划线展示） */
    private BigDecimal originalPrice;
    private Integer quantity;
    private BigDecimal subTotal;
    /** 该行优惠金额（直减 + 满减分摊） */
    private BigDecimal discountAmount;
    private String insuranceDetail;
    private BigDecimal insuranceFee;
    /** 售后标记：none/processing/refunded/exchanged/rejected（aftersale-service 回写） */
    private String afterSaleStatus;
    private Long afterSaleId;
    private Integer isDeleted;
    private LocalDateTime createdAt;
}
