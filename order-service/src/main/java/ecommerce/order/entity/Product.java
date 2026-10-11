package ecommerce.order.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品快照实体（order-service 内查 products 表的精简版）。
 * <p>
 * 加购用 name/image/price/description 做快照；下单还要 stock 校验/扣减库存。
 */
@Data
public class Product {
    private Long id;
    private String name;
    private String image;
    private BigDecimal salesPrice;
    private String description;
    private Integer stock;
}
