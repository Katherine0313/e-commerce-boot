package ecommerce.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 商品 VO。
 * <p>
 * 占位骨架：商品表结构尚未设计（阶段 2/3），这里的字段是给前端联调用的示例结构。
 * 按已确认决策，商品是单规格（不做 SKU），价格/库存直接挂在商品上。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVO {
    /** 商品 ID */
    @Schema(description = "商品 ID")
    private Long id;
    /** 商品名称 */
    @Schema(description = "商品名称")
    private String name;
    /** 商品价格 */
    @Schema(description = "商品价格（单规格，价格直接挂在商品上）")
    private BigDecimal price;
    /** 库存数量 */
    @Schema(description = "库存数量")
    private Integer stock;
    /** 主图 URL */
    @Schema(description = "主图 URL")
    private String image;
    /** 商品描述 */
    @Schema(description = "商品描述")
    private String description;
    /** 所属分类 ID */
    @Schema(description = "所属分类 ID")
    private Long categoryId;
    /** 上架状态 */
    @Schema(description = "上架状态：1 上架 0 下架")
    private Integer status;
}
