package ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 更新购物车数量请求。
 */
@Data
public class UpdateQuantityDTO {
    @Schema(description = "数量（最小 1）")
    private Integer quantity;
}
