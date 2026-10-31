package ecommerce.auth.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 管理员信息（登录返回 / 当前用户查询返回），不含 password。
 */
@Data
@Builder
public class AdminUserVO {
    private Long id;
    private String username;
    private String name;
    /** RBAC 角色标识 */
    private List<String> roles;
    /** 权限标识 */
    private List<String> permissions;
    /** 可见菜单树 */
    private List<MenuVO> menus;
}
