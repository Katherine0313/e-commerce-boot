package ecommerce.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 接口文档人工补充字段 */
@Data
public class ApiDocUpdateDTO {

    @Schema(description = "接口中文名，留空则展示 OpenAPI summary")
    private String apiName;

    @Schema(description = "是否公共接口：1 是 0 否")
    private Integer isPublic;

    @Schema(description = "返回示例，必须为合法 JSON 文本")
    private String responseExample;

    @Schema(description = "错误码，必须为合法 JSON 文本")
    private String errorCodes;
}
