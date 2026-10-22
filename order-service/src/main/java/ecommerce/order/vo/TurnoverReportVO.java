package ecommerce.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 营业额折线报表 VO（后台工作台 /api/admin/report/turnover-report，给 ECharts 画折线图）。
 * <p>
 * dateList 与 turnoverList 一一对应，粒度按天（yyyy-MM-dd）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "营业额折线报表VO（给ECharts）")
public class TurnoverReportVO {
    @Schema(description = "日期列表，和营业额一一对应，例：2026-10-01,2026-10-02")
    private List<String> dateList;
    @Schema(description = "营业额列表，和日期一一对应，例：120.50,340.00")
    private List<BigDecimal> turnoverList;
}
