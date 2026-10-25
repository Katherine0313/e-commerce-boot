package ecommerce.aiagent.service.impl;

import ecommerce.aiagent.agent.Intent;
import ecommerce.aiagent.agent.IntentRouter;
import ecommerce.aiagent.rag.RagRetriever;
import ecommerce.aiagent.service.AiChatService;
import ecommerce.aiagent.tool.OrderTool;
import ecommerce.aiagent.tool.ProductTool;
import ecommerce.aiagent.vo.ChatReplyVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 导购 Agent 编排实现：意图路由 → 按意图组合 system prompt + 工具 / RAG → 大模型生成回复。
 * <p>
 * 客服场景（scene=customer-service）暂不接 Tool/RAG，走固定 prompt + Redis 会话历史，
 * 后续再按需接入 function calling / RAG。
 */
@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {

    private static final String RECOMMEND_PROMPT =
            "用户想推荐或了解商品。请调用商品查询工具获取真实商品数据，再结合用户需求生成推荐方案（给出名称、价格、推荐理由）。";
    private static final String ORDER_PROMPT =
            "用户想下单。请先确认商品与数量，生成订单预览供用户确认；写操作必须经用户明确确认后才能执行。";
    /** 客服场景固定 system prompt（多轮上下文靠 Redis 历史拼接） */
    private static final String CUSTOMER_SERVICE_PROMPT =
            "你是杰米多多智能导购AI，请结合用户提出的需求耐心解答问题。";

    /** Redis 会话历史：key 前缀 + 最近 12 条消息（约 6 轮）+ 2 小时过期 */
    private static final String HISTORY_KEY_PREFIX = "agent:chat:history:";
    private static final int HISTORY_MAX_MESSAGES = 12;
    private static final Duration HISTORY_TTL = Duration.ofHours(2);

    private final ChatClient chatClient;
    private final IntentRouter intentRouter;
    private final ProductTool productTool;
    private final OrderTool orderTool;
    private final RagRetriever ragRetriever;
    private final StringRedisTemplate redis;

    public AiChatServiceImpl(ChatClient chatClient, IntentRouter intentRouter,
                             ProductTool productTool, OrderTool orderTool,
                             RagRetriever ragRetriever, StringRedisTemplate redis) {
        this.chatClient = chatClient;
        this.intentRouter = intentRouter;
        this.productTool = productTool;
        this.orderTool = orderTool;
        this.ragRetriever = ragRetriever;
        this.redis = redis;
    }

    @Override
    public ChatReplyVO chat(String message, String sessionId, String scene) {
        if (isCustomerService(scene)) {
            String reply = chatClient.prompt()
                    .system(CUSTOMER_SERVICE_PROMPT)
                    .user(buildPromptWithHistory(sessionId, message))
                    .call()
                    .content();
            saveHistory(sessionId, message, reply);
            return new ChatReplyVO(reply);
        }
        return new ChatReplyVO(chatLegacy(message));
    }

    @Override
    public Flux<String> chatStream(String message, String sessionId, String scene) {
        if (isCustomerService(scene)) {
            long start = System.currentTimeMillis();
            String prompt = buildPromptWithHistory(sessionId, message);
            log.info("AI 请求开始 sessionId={} promptLength={} 历史准备耗时={}ms",
                    sessionId, prompt.length(), System.currentTimeMillis() - start);
            StringBuilder replyBuilder = new StringBuilder();
            AtomicBoolean firstToken = new AtomicBoolean(true);
            return chatClient.prompt()
                    .system(CUSTOMER_SERVICE_PROMPT)
                    .user(prompt)
                    .stream()
                    .content()
                    // 逐块下发（前端首字即显示），同时累积完整回复写入 Redis
                    .doOnNext(chunk -> {
                        if (firstToken.compareAndSet(true, false)) {
                            log.info("AI 首字耗时={}ms sessionId={}",
                                    System.currentTimeMillis() - start, sessionId);
                        }
                        replyBuilder.append(chunk);
                    })
                    .doOnComplete(() -> {
                        log.info("AI 流完成总耗时={}ms sessionId={}",
                                System.currentTimeMillis() - start, sessionId);
                        saveHistory(sessionId, message, replyBuilder.toString());
                    })
                    .doOnError(e -> log.warn("AI 流式对话失败 sessionId={}", sessionId, e));
        }
        return chatStreamLegacy(message);
    }

    // ==================== 原导购 Agent（保留，后续接 Tool/RAG） ====================

    private String chatLegacy(String message) {
        Intent intent = intentRouter.route(message);
        return switch (intent) {
            case RECOMMEND -> chatClient.prompt()
                    .system(RECOMMEND_PROMPT)
                    .tools(productTool)
                    .user(message)
                    .call()
                    .content();
            case ORDER -> chatClient.prompt()
                    .system(ORDER_PROMPT)
                    .tools(orderTool)
                    .user(message)
                    .call()
                    .content();
            case QA -> chatClient.prompt()
                    .user(buildRagPrompt(message))
                    .call()
                    .content();
            default -> chatClient.prompt().user(message).call().content();
        };
    }

    private Flux<String> chatStreamLegacy(String message) {
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

    // ==================== 客服会话历史（Redis） ====================

    private boolean isCustomerService(String scene) {
        return scene != null && "customer-service".equalsIgnoreCase(scene.trim());
    }

    /** 会话 ID 只做简单清洗：去空格、限长，避免异常 key */
    private String normalizeSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        String sid = sessionId.trim();
        return sid.length() > 64 ? sid.substring(0, 64) : sid;
    }

    private String historyKey(String sessionId) {
        return HISTORY_KEY_PREFIX + sessionId;
    }

    /** 读取最近几轮历史，拼成「用户/助手」文本，末尾追加本轮用户消息 */
    private String buildPromptWithHistory(String sessionId, String message) {
        String sid = normalizeSessionId(sessionId);
        StringBuilder sb = new StringBuilder();
        if (sid != null) {
            try {
                List<String> rows = redis.opsForList().range(historyKey(sid), -HISTORY_MAX_MESSAGES, -1);
                if (rows != null) {
                    for (String row : rows) {
                        if (row.startsWith("user:")) {
                            sb.append("用户：").append(row.substring(5)).append('\n');
                        } else if (row.startsWith("assistant:")) {
                            sb.append("助手：").append(row.substring(10)).append('\n');
                        }
                    }
                }
            } catch (Exception e) {
                // Redis 不可用时退化为单轮，不影响对话
                log.warn("读取会话历史失败 sessionId={}", sid, e);
            }
        }
        sb.append("用户：").append(message);
        return sb.toString();
    }

    /** 保存本轮对话（用户 + 助手），只保留最近 N 条，2 小时过期 */
    private void saveHistory(String sessionId, String userMessage, String reply) {
        String sid = normalizeSessionId(sessionId);
        if (sid == null || !StringUtils.hasText(reply)) {
            return;
        }
        String key = historyKey(sid);
        try {
            redis.opsForList().rightPush(key, "user:" + userMessage);
            redis.opsForList().rightPush(key, "assistant:" + reply);
            redis.opsForList().trim(key, -HISTORY_MAX_MESSAGES, -1);
            redis.expire(key, HISTORY_TTL);
        } catch (Exception e) {
            log.warn("保存会话历史失败 sessionId={}", sid, e);
        }
    }
}
