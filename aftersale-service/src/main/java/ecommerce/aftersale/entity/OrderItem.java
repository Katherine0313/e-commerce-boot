package ecommerce.aftersale.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 订单项实体（aftersale 域内只读快照，仅售后需要的字段）。
 * <p>
 * 申请售后时从 order_items 表读取，快照到 after_sale_items。
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
}
