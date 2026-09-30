package ecommerce.auth.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 管理员信息（登录返回 / 当前用户查询返回），不含 password。
 */
@Data
@Builder
public class AdminUserVO {
    private Long id;
    private String username;
    private String name;
    private String role;
}
