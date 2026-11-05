package ecommerce.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商品评价标签统计（/api/reviews/product/{id}/tags 返回）。
 * 固定返回 8 个标签，数量为 0 的也返回，前端置灰展示。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewTagStatVO {

    @Schema(description = "标签值")
    private String value;

    @Schema(description = "标签展示名（与 value 相同，保留字段便于以后扩展 code）")
    private String label;

    @Schema(description = "该标签的评价条数")
    private Long count;
}
