package ecommerce.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 表字段信息行 */
@Data
public class DbColumnVO {

    @Schema(description = "字段名")
    private String name;

    @Schema(description = "字段类型")
    private String type;

    @Schema(description = "是否允许为空")
    private Boolean nullable;

    @Schema(description = "默认值")
    private String defaultValue;

    @Schema(description = "额外属性，如 auto_increment")
    private String extra;

    @Schema(description = "键类型，如 PRI / UNI")
    private String columnKey;

    @Schema(description = "字段备注")
    private String comment;
}
