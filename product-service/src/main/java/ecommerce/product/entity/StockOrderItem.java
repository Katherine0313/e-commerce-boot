package ecommerce.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockOrderItem {
    private Long id;
    private Long orderId;
    private Long skuId;
    private String skuCode;
    private String skuName;
    private String specText;
    private Integer qty;
    private BigDecimal costPrice;
}
