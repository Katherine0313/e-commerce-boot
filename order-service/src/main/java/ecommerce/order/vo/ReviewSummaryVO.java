package ecommerce.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * C 端商品评价统计 VO（/api/reviews/product/{productId}/summary 返回）。
 * <p>
 * realCount / virtualCount 只给后台看（后台列表另有统计），C 端**不返回**，
 * 避免消费者看出「有几条是系统评价」这个总数；每条评价自己带 isVirtual 标记，前端打「系统评价」标签。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSummaryVO {

    @Schema(description = "可见评价总数")
    private Long totalCount;

    @Schema(description = "平均星级（保留 1 位小数，无评价时为 0）")
    private Double avgRating;

    @Schema(description = "好评率：4~5 星占比（0~1，无评价时为 0）")
    private Double goodRate;

    @Schema(description = "带图/视频的评价条数")
    private Long withMediaCount;

    @Schema(description = "5 星条数")
    private Long fiveStar;

    @Schema(description = "4 星条数")
    private Long fourStar;

    @Schema(description = "3 星条数")
    private Long threeStar;

    @Schema(description = "2 星条数")
    private Long twoStar;

    @Schema(description = "1 星条数")
    private Long oneStar;
}
