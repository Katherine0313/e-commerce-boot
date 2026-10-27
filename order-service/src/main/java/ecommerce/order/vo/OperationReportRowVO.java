package ecommerce.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 运营数据报表：单日明细。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "运营数据报表单日明细")
public class OperationReportRowVO {

    @Schema(description = "日期")
    private LocalDate date;

    @Schema(description = "当日销售额（实收 − 退款，按支付日归属）")
    private BigDecimal sales;

    @Schema(description = "当日有效支付订单数（已支付、未取消、未全额退款）")
    private long effectivePaidOrders;

    @Schema(description = "支付转化率（当日提交且支付成功过的订单 / 当日提交订单，0~1）")
    private double paymentConversionRate;

    @Schema(description = "平均客单价（当日销售额 / 有效支付订单数）")
    private BigDecimal averageOrderValue;

    @Schema(description = "当日新增用户数")
    private long newUsers;
}