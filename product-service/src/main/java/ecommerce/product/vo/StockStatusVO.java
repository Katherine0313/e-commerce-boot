package ecommerce.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 库存状态常量（high/normal/low 的中文与颜色），对齐旧 constants/dict.js 的 STOCK_STATUS。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockStatusVO {
    @Schema(description = "状态 key → 中文，如 high→高库存")
    private Map<String, String> map;
    @Schema(description = "状态 key → 颜色，如 high→blue")
    private Map<String, String> color;
}
