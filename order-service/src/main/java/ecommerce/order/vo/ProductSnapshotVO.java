package ecommerce.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商品快照（后台建虚拟评价时从 products 表取，不信任前端传的商品名/图）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSnapshotVO {

    @Schema(description = "商品 ID")
    private Long id;

    @Schema(description = "商品标题（title 为空时退回 name）")
    private String title;

    @Schema(description = "商品主图")
    private String image;
}
