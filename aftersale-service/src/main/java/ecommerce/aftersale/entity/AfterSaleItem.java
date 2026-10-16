package ecommerce.aftersale.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后明细实体（对应 after_sale_items 表）。
 * <p>
 * product_name/image/sales_price 是申请售后时从订单项快照的字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSaleItem {
    private Long id;
    private Long shopId;
    private Long afterSaleId;
    private Long orderItemId;
    private Long productId;
    private String productName;
    private String image;
    private BigDecimal salesPrice;
    private Integer quantity;
    private BigDecimal refundAmount;
    private String insuranceDetail;
    private BigDecimal insuranceFee;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
