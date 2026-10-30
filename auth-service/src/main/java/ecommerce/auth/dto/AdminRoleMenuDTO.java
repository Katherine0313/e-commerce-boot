package ecommerce.auth.dto;

import lombok.Data;

import java.util.List;

/**
 * 角色分配菜单请求。
 */
@Data
public class AdminRoleMenuDTO {
    private List<Long> menuIds;
}
