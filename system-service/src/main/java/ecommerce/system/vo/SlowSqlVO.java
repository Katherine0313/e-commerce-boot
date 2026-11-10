package ecommerce.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 慢 SQL 明细行 */
@Data
public class SlowSqlVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "服务名，如 product-service")
    private String serviceName;

    @Schema(description = "Mapper 方法（MappedStatement id）")
    private String mapperMethod;

    @Schema(description = "SQL 类型：SELECT/INSERT/UPDATE/DELETE")
    private String sqlType;

    @Schema(description = "归一化 SQL 的 MD5 指纹")
    private String sqlFingerprint;

    @Schema(description = "归一化 SQL（不含参数值）")
    private String sqlText;

    @Schema(description = "执行耗时（毫秒）")
    private Integer costMs;

    @Schema(description = "返回/影响行数")
    private Integer rowCount;

    @Schema(description = "记录时间")
    private LocalDateTime createdAt;
}
