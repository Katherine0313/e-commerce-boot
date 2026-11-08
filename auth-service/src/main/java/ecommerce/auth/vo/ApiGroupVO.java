package ecommerce.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 接口树分组节点 */
@Data
public class ApiGroupVO {

    @Schema(description = "前端树节点 key")
    private String key;

    @Schema(description = "分组名")
    private String title;

    @Schema(description = "所属服务")
    private String serviceName;

    @Schema(description = "接口数量")
    private Integer count;

    @Schema(description = "接口叶子节点")
    private List<ApiItemVO> children;
}
