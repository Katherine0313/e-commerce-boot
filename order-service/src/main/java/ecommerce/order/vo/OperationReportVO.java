package ecommerce.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 运营数据报表（近 30 天）：概览 + 每日明细。
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Schema(description = "运营数据报表(近30天)")
public class OperationReportVO {

    @Schema(description = "统计开始日期（含）")
    private LocalDate begin;

    @Schema(description = "统计结束日期（含）")
    private LocalDate end;

    @Schema(description = "总销售额（实收 − 退款）")
    private BigDecimal totalSales;

    @Schema(description = "有效支付订单总数")
    private long totalEffectivePaidOrders;

    @Schema(description = "支付转化率（30 天提交且支付成功过的订单 / 30 天提交订单，0~1）")
    private double paymentConversionRate;

    @Schema(description = "平均客单价（总销售额 / 有效支付订单总数）")
    private BigDecimal averageOrderValue;

    @Schema(description = "新增用户总数")
    private long totalNewUsers;

    @Schema(description = "退款订单数（30 天内去重订单数）")
    private long totalRefundOrders;

    @Schema(description = "每日明细（按日期升序）")
    private List<OperationReportRowVO> rows;
}