package ecommerce.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurnoverReportVO {
    @Schema(description = "日期列表")
    private List<String> dateList;
    @Schema(description = "营业额列表")
    private List<BigDecimal> turnoverList;
}
