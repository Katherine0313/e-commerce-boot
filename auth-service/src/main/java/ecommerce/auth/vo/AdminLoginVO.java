package ecommerce.auth.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 管理员登录响应：{token, user}，与 Node 后端结构对齐。
 * 前端登录流程只读 token（写入 localStorage），user 用于展示/后续扩展。
 */
@Data
@Builder
public class AdminLoginVO {
    private String token;
    private AdminUserVO user;
}
