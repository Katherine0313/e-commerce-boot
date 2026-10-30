package ecommerce.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 后台角色新增/编辑请求。
 */
@Data
public class AdminRoleDTO {
    @Schema(description = "角色标识，如 operator")
    private String roleKey;
    @Schema(description = "角色名称")
    private String roleName;
    @Schema(description = "状态：1 正常 0 停用")
    private Integer status;
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "分配的菜单 ID（编辑时可选）")
    private List<Long> menuIds;
}
