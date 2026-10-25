package ecommerce.aiagent.controller;

import ecommerce.aiagent.dto.ChatRequestDTO;
import ecommerce.aiagent.service.AiChatService;
import ecommerce.aiagent.vo.ChatReplyVO;
import ecommerce.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * AI 导购助手对话接口（免登录，见 common 的 EXCLUDE_AUTH_PATHS）。
 */
@Tag(name = "AI 导购助手", description = "悬浮式 AI 导购：对话、商品推荐、下单（下单待 order-service 就绪）")
@RestController
@RequestMapping("/api/agent")
public class AiChatController {

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @Operation(summary = "AI 对话（非流式）")
    @PostMapping("/chat")
    public Result<ChatReplyVO> chat(@RequestBody ChatRequestDTO dto) {
        return Result.success(aiChatService.chat(dto.getMessage(), dto.getSessionId(), dto.getScene()));
    }

    @Operation(summary = "AI 对话（SSE 流式，首字即推送）")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody ChatRequestDTO dto) {
        return aiChatService.chatStream(dto.getMessage(), dto.getSessionId(), dto.getScene())
                // 用 ServerSentEvent 包装，回复里的换行会按 SSE 规范编码，不会破坏事件边界
                .map(data -> ServerSentEvent.builder(data).build());
    }
}
