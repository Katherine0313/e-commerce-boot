package ecommerce.system.dto;

import lombok.Data;

/**
 * 管理员重置密码请求。
 */
@Data
public class ResetPasswordDTO {
    private String password;
}
