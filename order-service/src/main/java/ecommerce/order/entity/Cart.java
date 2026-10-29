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
