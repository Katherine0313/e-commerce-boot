package ecommerce.auth.dto;

import lombok.Data;

/**
 * 管理员登录请求体。
 */
@Data
public class AdminLoginDTO {
    private String username;
    private String password;
}
