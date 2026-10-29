package ecommerce.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 商品实体（单规格：价格/库存直接挂在商品上，不做 SKU）。
 * <p>
 * costPrice 是后台字段，C 端查询（listOnSale/findById）不下发，仅后台接口会 SELECT 它。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    private Long id;
    private Long categoryId;
    private Long tagId;
    private String name;
    private String subTitle;
    private String brand;
    private String description;
    private String image;
    private String body;
    private String salesMode;
    private Integer listingStatus;
    private BigDecimal salesPrice;
    private BigDecimal marketPrice;
    private BigDecimal costPrice;
    private String productCode;
    private Integer stock;
    private BigDecimal weight;
    private BigDecimal volume;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}