package ecommerce.shop.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 沙箱管理员简要信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBriefVO {
    @Schema(description = "管理员 ID")
    private Long id;
    @Schema(description = "用户名")
    private String username;
    @Schema(description = "姓名")
    private String name;
    @Schema(description = "角色")
    private String role;
}
