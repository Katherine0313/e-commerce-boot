package ecommerce.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 库存预警配置实体（stock 表）。
 * <p>
 * 只存「预警配置」：min_stock / max_stock / alert_enabled；当前库存 stock 在 products.stock（不归本表管）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Stock {
    private Long id;
    private Long productId;
    /** 预警是否启用：1 启用 0 禁用 */
    private Integer alertEnabled;
    private Integer minStock;
    private Integer maxStock;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
