package ecommerce.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 工作台顶部累计销售额。
 * <p>
 * 口径与营业额报表一致：实收（pay_at 非空的 pay_amount 合计）− 退款
 * （status=refunded 的售后明细 refund_amount 合计），不受分页影响。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工作台顶部累计销售额")
public class HeadlineVO {
    @Schema(description = "累计实收")
    private BigDecimal totalPaid;
    @Schema(description = "累计退款")
    private BigDecimal totalRefund;
    @Schema(description = "累计净销售额（实收 − 退款）")
    private BigDecimal netAmount;
    @Schema(description = "累计订单量（全时段，排除已取消）")
    private long totalOrders;
}
