package ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 提交评价请求。
 * <p>
 * 订单归属与状态由后端按路径里的 orderId 校验；product_id 用于定位订单内被评价的商品
 * （前端取订单首个商品，后端据此从 order_items 快照里取 product_name/image，不信任前端）。
 */
@Data
public class CreateReviewDTO {
    @Schema(description = "被评价的商品 ID（订单明细里的商品，可为空时取订单首个商品）")
    private Long productId;
    @Schema(description = "星级：1~5")
    private Integer rating;
    @Schema(description = "旧单值标签，兼容旧客户端；新客户端用 tags")
    private String tag;
    @Schema(description = "评价标签数组，最多 4 个")
    private List<String> tags;
    @Schema(description = "评价内容（至少 5 字）")
    private String content;
    @Schema(description = "晒图 JSON 数组（可选）")
    private String images;
}
