package ecommerce.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 后台账号新增/编辑请求。
 */
@Data
public class AdminAccountDTO {
    @Schema(description = "登录用户名（新增后不可改）")
    private String username;
    @Schema(description = "密码：新增时必填；编辑时忽略，重置密码走单独接口")
    private String password;
    @Schema(description = "显示名")
    private String name;
    @Schema(description = "手机号")
    private String phone;
    @Schema(description = "状态：1 正常 0 停用")
    private Integer status;
    @Schema(description = "角色 ID 列表")
    private List<Long> roleIds;
}
