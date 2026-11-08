package ecommerce.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 后台登录日志列表项。
 * <p>
 * 驼峰命名，与 auth-service 其它 VO 保持一致（不加 @JsonNaming(SnakeCase)）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLoginLogVO {
    @Schema(description = "日志 ID")
    private Long id;
    @Schema(description = "管理员 ID；账号不存在时为空")
    private Long adminId;
    @Schema(description = "登录名（原样保留）")
    private String username;
    @Schema(description = "LOGIN / LOGOUT")
    private String loginType;
    @Schema(description = "登录结果码")
    private String resultCode;
    @Schema(description = "失败原因")
    private String reason;
    @Schema(description = "客户端 IP")
    private String ip;
    @Schema(description = "浏览器 User-Agent")
    private String userAgent;
    @Schema(description = "发生时间")
    private LocalDateTime createdAt;
}
