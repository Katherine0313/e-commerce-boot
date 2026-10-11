package ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 清空购物车请求（body 可选）。
 * <p>
 * productIds 为空则清空整个购物车；非空则只移除列出的商品。
 */
@Data
public class ClearCartDTO {
    @Schema(description = "要移除的商品 id 列表；为空则清空整个购物车")
    private List<Long> productIds;
}
