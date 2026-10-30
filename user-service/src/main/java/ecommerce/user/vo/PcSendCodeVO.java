package ecommerce.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PC 发送验证码结果。
 * 当前未接短信服务，code 直接返回给前端用于演示；接入真实短信后应删除该字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PcSendCodeVO {
    @Schema(description = "模拟验证码（生产接短信后不再返回）", example = "123456")
    private String code;

    @Schema(description = "有效期（秒）", example = "300")
    private Integer expireIn;
}
