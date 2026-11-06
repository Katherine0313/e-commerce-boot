package ecommerce.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Cart {
    private Long id;
    private Long userId;
    private Long productId;
    /** SKU ID；旧数据/单规格可为空，读取时回落到商品默认 SKU */
    private Long skuId;
    /** 规格快照（JSON 字符串） */
    private String specJson;
    /** 商家编码快照 */
    private String skuCode;
    private Integer quantity;
    private String name;
    private String image;
    private BigDecimal price;
    private String description;
    private String insuranceDetail;
    private BigDecimal insuranceFee;
    private Integer stock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
