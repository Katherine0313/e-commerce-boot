package ecommerce.aiagent.service;

import ecommerce.aiagent.vo.ChatReplyVO;
import reactor.core.publisher.Flux;

public interface AiChatService {

    /** 一问一答（非流式） */
    ChatReplyVO chat(String message);

    /** SSE 流式对话（打字机输出） */
    Flux<String> chatStream(String message);
}
