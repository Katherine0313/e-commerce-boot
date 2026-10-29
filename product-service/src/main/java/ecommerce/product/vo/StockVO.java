package ecommerce.product.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 库存预警列表 VO（LEFT JOIN products + stock，后台 /api/admin/stock）。
 * <p>
 * 字段统一驼峰（后台 Admin 端对接契约）；预警状态（high/normal/low）由前端拿 stock 与上下限实时算，不下发。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockVO {
    @Schema(description = "预警配置 ID（未配置时为 null）")
    private Long stockId;
    @Schema(description = "商品 ID")
    private Long productId;
    @Schema(description = "商品名称")
    private String name;
    @Schema(description = "商品主图")
    private String image;
    @Schema(description = "主标题")
    private String title;
    @Schema(description = "当前库存")
    private Integer stock;
    @Schema(description = "库存下限")
    private Integer minStock;
    @Schema(description = "库存上限")
    private Integer maxStock;
    @Schema(description = "预警是否启用：1 启用 0 禁用")
    private Integer alertEnabled;
    @Schema(description = "配置创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @Schema(description = "配置更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
