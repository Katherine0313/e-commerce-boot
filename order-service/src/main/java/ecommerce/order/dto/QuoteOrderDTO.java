package ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 确认页报价请求：只传商品和数量，价格由后端实时查询并计算。
 */
@Data
public class QuoteOrderDTO {
    @Schema(description = "商品明细")
    private List<OrderItemDTO> items;
    @Schema(description = "选用的用户券 ID，不传表示不用券")
    private Long couponId;
}
