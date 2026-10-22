package ecommerce.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 营业额卡片 VO（后台工作台 /api/admin/orders/turnover）。
 * <p>
 * 一次请求只返回选中时间维度的一项营业额；口径 = 实收（pay_at 区间内 pay_amount 合计）
 * − 退款（对应订单已退款成功售后单的 refund_amount 合计），退款跟着原订单 pay_at 归属。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "营业额卡片（切换时间维度）")
public class TurnoverVO {
    @Schema(description = "维度名称，比如：昨日、近7天、本月，前端用来显示标题")
    private String timeDesc;
    @Schema(description = "营业额")
    private BigDecimal turnover;
}
