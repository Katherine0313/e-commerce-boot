package ecommerce.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * product_sku 的精简视图：StockWriteService 只需要这 4 个字段（比完整 ProductSku 少很多）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSkuLite {
    private Long id;
    private Long productId;
    private String skuCode;
    private Integer stock;
}
