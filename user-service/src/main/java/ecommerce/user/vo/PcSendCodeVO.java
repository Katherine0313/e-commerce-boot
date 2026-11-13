package ecommerce.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PC 发送验证码结果。
 * 验证码通过短信下发，接口不返回验证码本身。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PcSendCodeVO {

    @Schema(description = "是否发送成功", example = "true")
    private Boolean success;
}
