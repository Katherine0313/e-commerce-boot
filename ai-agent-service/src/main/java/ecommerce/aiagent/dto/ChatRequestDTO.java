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

    @Schema(description = "会话 ID（前端生成，用于 Redis 多轮上下文；为空则单轮）")
    private String sessionId;

    @Schema(description = "场景：customer-service 客服对话（走固定客服 prompt，暂不接 Tool/RAG）；为空走原导购 Agent")
    private String scene;
}
