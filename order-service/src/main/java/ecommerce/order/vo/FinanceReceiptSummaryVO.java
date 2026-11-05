package ecommerce.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 收款单统计卡。
 * <p>
 * 已审核实收按 finance_receipt.paid_at 区间统计；退款金额只读 after_sales，不在财务表落冗余。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceReceiptSummaryVO {

    @Schema(description = "已审核实收合计")
    private BigDecimal auditedAmount;

    @Schema(description = "区间退款金额（售后已退款）")
    private BigDecimal refundAmount;

    @Schema(description = "净收款 = 已审核实收 - 退款金额")
    private BigDecimal netAmount;

    @Schema(description = "待审核笔数（全时段）")
    private long pendingCount;
}
