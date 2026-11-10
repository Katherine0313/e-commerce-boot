package ecommerce.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 慢 SQL 指纹聚合行（Top N） */
@Data
public class SlowSqlTopVO {

    @Schema(description = "SQL 指纹")
    private String fingerprint;

    @Schema(description = "归一化 SQL 示例")
    private String sqlText;

    @Schema(description = "SQL 类型")
    private String sqlType;

    @Schema(description = "涉及服务，逗号分隔")
    private String services;

    @Schema(description = "执行次数")
    private Long count;

    @Schema(description = "平均耗时（毫秒）")
    private Long avgCostMs;

    @Schema(description = "最大耗时（毫秒）")
    private Long maxCostMs;

    @Schema(description = "最近出现时间")
    private LocalDateTime lastAt;
}
