package ecommerce.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 后台操作日志列表项。
 * <p>
 * 驼峰命名，与 auth-service 其它 VO 保持一致（不加 @JsonNaming(SnakeCase)），
 * 前端直接取字段，不做蛇形转换。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminOperationLogVO {
    @Schema(description = "日志 ID")
    private Long id;
    @Schema(description = "操作人 ID")
    private Long userId;
    @Schema(description = "操作人类型：admin")
    private String userType;
    @Schema(description = "操作人账号")
    private String username;
    @Schema(description = "业务模块，如 system")
    private String module;
    @Schema(description = "动作，如 USER_ADD")
    private String action;
    @Schema(description = "业务对象 ID")
    private Long bizId;
    @Schema(description = "变更详情")
    private String detail;
    @Schema(description = "HTTP 方法")
    private String method;
    @Schema(description = "请求路径")
    private String uri;
    @Schema(description = "客户端 IP")
    private String ip;
    @Schema(description = "请求参数（敏感字段已脱敏）")
    private String params;
    @Schema(description = "是否成功")
    private Boolean success;
    @Schema(description = "失败原因")
    private String errorMsg;
    @Schema(description = "耗时（毫秒）")
    private Long costMs;
    @Schema(description = "发生时间")
    private LocalDateTime createdAt;
}
