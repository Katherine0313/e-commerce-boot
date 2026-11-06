package ecommerce.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 商品实体（SPU）。价格/库存字段是 SKU 的聚合冗余：售价取最低 SKU、库存求和。
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
    private String title;
    private String image;
    private String body;
    private String salesMode;
    private Integer listingStatus;
    private BigDecimal salesPrice;
    private BigDecimal marketPrice;
    private BigDecimal costPrice;
    private String productCode;
    private Integer stock;
    /** 规格参数（JSON 数组字符串：[{"name":"屏幕","value":"6.1英寸"}]，非 SKU） */
    private String specs;
    /** 0 单规格 / 1 多规格 */
    private Integer skuType;
    /** 销售规格维度定义 JSON：[{"groupId":1,"name":"颜色","values":["曜石黑"]}] */
    private String skuSpecs;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}