package ecommerce.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 后台发券结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponGrantResultVO {
    @Schema(description = "成功发放数量")
    private Integer granted;
    @Schema(description = "跳过数量（已领取 / 库存不足等）")
    private Integer skipped;
    @Schema(description = "剩余库存")
    private Integer remainQuantity;
}
