package ecommerce.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 慢 SQL 按小时趋势 */
@Data
public class SlowSqlTrendVO {

    @Schema(description = "小时，格式 yyyy-MM-dd HH:00")
    private String hour;

    @Schema(description = "慢 SQL 条数")
    private Long count;

    @Schema(description = "平均耗时（毫秒）")
    private Long avgCostMs;

    @Schema(description = "最大耗时（毫秒）")
    private Long maxCostMs;
}
