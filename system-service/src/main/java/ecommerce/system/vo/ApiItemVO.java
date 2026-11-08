package ecommerce.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 接口树叶子节点 */
@Data
public class ApiItemVO {

    @Schema(description = "前端树节点 key")
    private String key;

    @Schema(description = "接口文档 ID")
    private Long id;

    @Schema(description = "展示名：人工中文名优先，其次 summary，最后 path")
    private String title;

    @Schema(description = "请求方法")
    private String method;

    @Schema(description = "请求路径")
    private String path;

    @Schema(description = "OpenAPI summary")
    private String summary;

    @Schema(description = "本次同步是否未出现")
    private Boolean missing;
}
