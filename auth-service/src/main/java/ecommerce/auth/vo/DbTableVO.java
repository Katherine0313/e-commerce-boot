package ecommerce.auth.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 数据库表清单行 */
@Data
public class DbTableVO {

    @Schema(description = "表名")
    private String name;

    @Schema(description = "表备注")
    private String comment;

    @Schema(description = "存储引擎")
    private String engine;

    @Schema(description = "数据字节数（不含索引）")
    private Long dataLength;

    @Schema(description = "格式化后的大小，如 14.5 MB")
    private String dataLengthHuman;

    @Schema(description = "InnoDB 估算行数")
    private Long rows;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "最后更新时间")
    private LocalDateTime updateTime;
}
