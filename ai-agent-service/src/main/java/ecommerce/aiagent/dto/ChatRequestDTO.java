package ecommerce.aiagent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 对话请求入参。
 */
@Data
public class ChatRequestDTO {
    @Schema(description = "用户输入的消息", example = "推荐一款送给程序员的键盘")
    private String message;
}
