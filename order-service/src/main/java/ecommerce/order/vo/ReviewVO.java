package ecommerce.order.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商品评价 VO（C 端返回）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewVO {
    @Schema(description = "评价 ID")
    private Long id;
    @Schema(description = "所属订单 ID")
    private Long orderId;
    @Schema(description = "商品 ID")
    private Long productId;
    @Schema(description = "商品名快照")
    private String productName;
    @Schema(description = "商品图快照")
    private String productImage;
    @Schema(description = "星级：1~5")
    private Integer rating;
    @Schema(description = "评价标签")
    private String tag;
    @Schema(description = "评价内容")
    private String content;
    @Schema(description = "评价时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
