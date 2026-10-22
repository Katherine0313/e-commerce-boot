package ecommerce.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 工作台统计卡 VO（/api/admin/report/dashboard）。
 * <p>
 * 四张统计卡中除「用户总量」外的三项由 order-service 提供；
 * 营业额口径与 /turnover 一致（pay_at 实收 − refunded 退款）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工作台统计卡")
public class DashboardStatsVO {
    @Schema(description = "今日营业额（实收 − 退款）")
    private BigDecimal todayTurnover;
    @Schema(description = "昨日营业额（实收 − 退款），用于今日环比")
    private BigDecimal yesterdayTurnover;
    @Schema(description = "今日订单量（当天创建、不含已取消）")
    private long todayOrders;
    @Schema(description = "昨日订单量（用于今日环比）")
    private long yesterdayOrders;
    @Schema(description = "待发货订单数")
    private long pendingShip;
}
