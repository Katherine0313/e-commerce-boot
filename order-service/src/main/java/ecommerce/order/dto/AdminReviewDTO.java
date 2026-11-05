package ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 后台新增/编辑虚拟评价请求体。
 * <p>
 * product_name / product_image **不接受前端传** —— 服务端从 products 表取快照，
 * 跟买家端评价从 order_items 取快照一个道理，不信任前端。
 */
@Data
public class AdminReviewDTO {

    @Schema(description = "商品 ID（虚拟评价必填，必须是真实存在的商品）")
    private Long productId;

    @Schema(description = "星级：1~5")
    private Integer rating;

    @Schema(description = "旧单值标签，兼容旧前端；新前端用 tags")
    private String tag;

    @Schema(description = "评价标签数组，最多 4 个")
    private List<String> tags;

    @Schema(description = "评价内容，至少 5 个字")
    private String content;

    @Schema(description = "媒体 JSON，如 [{\"url\":\"/image/review/x.png\",\"type\":\"image\"}]")
    private String images;
}
