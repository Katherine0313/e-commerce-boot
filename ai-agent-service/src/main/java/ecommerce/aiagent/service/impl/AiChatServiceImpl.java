package ecommerce.aiagent.service.impl;

import ecommerce.aiagent.agent.Intent;
import ecommerce.aiagent.agent.IntentRouter;
import ecommerce.aiagent.rag.RagRetriever;
import ecommerce.aiagent.service.AiChatService;
import ecommerce.aiagent.tool.OrderTool;
import ecommerce.aiagent.tool.ProductTool;
import ecommerce.aiagent.vo.ChatReplyVO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * AI 导购 Agent 编排实现：意图路由 → 按意图组合 system prompt + 工具 / RAG → 大模型生成回复。
 * <p>
 * 这就是「Graph 工作流」的轻量落地：IntentRouter 分流，不同意图走不同 tool/advisors 链。
 */
@Service
public class AiChatServiceImpl implements AiChatService {

    private static final String RECOMMEND_PROMPT =
            "用户想推荐或了解商品。请调用商品查询工具获取真实商品数据，再结合用户需求生成推荐方案（给出名称、价格、推荐理由）。";
    private static final String ORDER_PROMPT =
            "用户想下单。请先确认商品与数量，生成订单预览供用户确认；写操作必须经用户明确确认后才能执行。";

    private final ChatClient chatClient;
    private final IntentRouter intentRouter;
    private final ProductTool productTool;
    private final OrderTool orderTool;
    private final RagRetriever ragRetriever;

    public AiChatServiceImpl(ChatClient chatClient, IntentRouter intentRouter,
                             ProductTool productTool, OrderTool orderTool, RagRetriever ragRetriever) {
        this.chatClient = chatClient;
        this.intentRouter = intentRouter;
        this.productTool = productTool;
        this.orderTool = orderTool;
        this.ragRetriever = ragRetriever;
    }

    @Override
    public ChatReplyVO chat(String message) {
        Intent intent = intentRouter.route(message);
        String reply;
        switch (intent) {
            case RECOMMEND -> reply = chatClient.prompt()
                    .system(RECOMMEND_PROMPT)
                    .tools(productTool)
                    .user(message)
                    .call()
                    .content();
            case ORDER -> reply = chatClient.prompt()
                    .system(ORDER_PROMPT)
                    .tools(orderTool)
                    .user(message)
                    .call()
                    .content();
            case QA -> {
                String prompt = buildRagPrompt(message);
                reply = chatClient.prompt().user(prompt).call().content();
            }
            default -> reply = chatClient.prompt().user(message).call().content();
        }
        return new ChatReplyVO(reply);
    }

    @Override
    public Flux<String> chatStream(String message) {
        Intent intent = intentRouter.route(message);
        return switch (intent) {
            case RECOMMEND -> chatClient.prompt()
                    .system(RECOMMEND_PROMPT)
                    .tools(productTool)
                    .user(message)
                    .stream()
                    .content();
            case ORDER -> chatClient.prompt()
                    .system(ORDER_PROMPT)
                    .tools(orderTool)
                    .user(message)
                    .stream()
                    .content();
            case QA -> chatClient.prompt()
                    .user(buildRagPrompt(message))
                    .stream()
                    .content();
            default -> chatClient.prompt().user(message).stream().content();
        };
    }

    /** RAG 问答：检索商品文档拼进 Prompt，回答基于真实资料、减少幻觉 */
    private String buildRagPrompt(String message) {
        String context = ragRetriever.retrieve(message, 3);
        if (context.isBlank()) {
            return message;
        }
        return "请基于以下真实商品资料回答用户问题，不要编造：\n" + context + "\n\n用户问题：" + message;
    }
}
