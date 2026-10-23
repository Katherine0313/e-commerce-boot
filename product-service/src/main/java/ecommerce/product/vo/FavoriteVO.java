package ecommerce.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收藏列表项：商品展示字段 + 收藏时间。
 * <p>
 * 下架商品也会返回（status=0），由前端打「已下架」标记；下架时收藏关系保留，重新上架后仍是已收藏状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteVO {
    @Schema(description = "商品 ID")
    private Long productId;
    @Schema(description = "商品名称")
    private String name;
    @Schema(description = "主图 URL（相对路径，前端补域名）")
    private String image;
    @Schema(description = "售价")
    private BigDecimal price;
    @Schema(description = "市场价/划线价")
    private BigDecimal marketPrice;
    @Schema(description = "库存数量")
    private Integer stock;
    @Schema(description = "上架状态：1 上架 0 下架")
    private Integer status;
    @Schema(description = "收藏时间")
    private LocalDateTime createdAt;
}
