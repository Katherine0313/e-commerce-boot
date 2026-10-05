package ecommerce.aiagent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话回复。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatReplyVO {
    @Schema(description = "AI 回复内容")
    private String reply;
}
