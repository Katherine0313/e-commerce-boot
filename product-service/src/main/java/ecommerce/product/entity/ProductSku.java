package ecommerce.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品 SKU（product_sku 表）。
 * <p>
 * specJson 是规格组合快照（如 {"颜色":"曜石黑","内存":"256G"}），
 * specKey 是按 sku_specs 顺序拼成的规范化串（如 颜色:曜石黑|内存:256G），
 * 单规格默认 SKU 用 specJson={}、specKey=__DEFAULT__。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSku {
    private Long id;
    private Long productId;
    private String skuCode;
    private String specJson;
    private String specKey;
    private BigDecimal price;
    private BigDecimal marketPrice;
    private BigDecimal costPrice;
    private Integer stock;
    private String image;
    /** 1 启用 / 0 停用 */
    private Integer status;
    private Integer sort;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
