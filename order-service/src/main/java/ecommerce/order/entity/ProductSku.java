package ecommerce.order.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * SKU 精简实体（order-service 内查 product_sku 表）。
 */
@Data
public class ProductSku {
    private Long id;
    private Long productId;
    private String skuCode;
    private String specJson;
    private String specKey;
    private BigDecimal price;
    private Integer stock;
    private Integer status;
}
