package ecommerce.product.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * SKU 库存列表视图（product_sku + products 的必要字段）。
 * <p>
 * 前端库存页需要商品名/主图/规格/库存/阈值，ProductSku 实体里没有这些冗余字段，
 * 单独用一个 VO 承载，避免为了展示去改实体。
 */
@Data
public class StockSkuVO {
    private Long id;                 // sku id
    private Long productId;
    private String productName;
    private String productImage;
    private String skuCode;
    private String specJson;
    private Integer stock;
    private Integer minStock;
    private Integer maxStock;
    private Integer status;
    private BigDecimal price;
}
