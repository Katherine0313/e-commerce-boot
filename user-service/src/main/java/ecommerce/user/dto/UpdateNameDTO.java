package ecommerce.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设置昵称入参：小程序 nickname 输入框填写的昵称
 */
@Data
public class UpdateNameDTO {
    /** 用户昵称 */
    @Schema(description = "用户昵称，1~32 个字符", example = "小明",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
}
