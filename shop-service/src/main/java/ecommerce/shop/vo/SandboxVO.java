package ecommerce.shop.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 开沙箱返回：沙箱店 admin 令牌 + 用户信息 + 店铺信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxVO {
    @Schema(description = "沙箱店 admin 令牌")
    private String token;
    @Schema(description = "沙箱店管理员信息")
    private AdminBriefVO user;
    @Schema(description = "沙箱店信息")
    private ShopVO shop;
}
