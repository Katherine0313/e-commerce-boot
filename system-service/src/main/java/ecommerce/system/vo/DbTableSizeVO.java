package ecommerce.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 表容量行 */
@Data
public class DbTableSizeVO {

    @Schema(description = "表名")
    private String name;

    @Schema(description = "InnoDB 估算行数")
    private Long rows;

    @Schema(description = "数据字节数")
    private Long dataLength;

    @Schema(description = "索引字节数")
    private Long indexLength;

    @Schema(description = "数据 + 索引字节数")
    private Long totalLength;

    @Schema(description = "格式化大小，如 14.5 MB")
    private String sizeHuman;
}
