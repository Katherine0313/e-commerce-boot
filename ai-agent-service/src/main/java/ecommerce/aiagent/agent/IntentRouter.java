package ecommerce.aiagent.agent;

import org.springframework.stereotype.Component;

/**
 * 意图路由（Graph 工作流入口）：把用户自然语言请求粗分为推荐/下单/问答/闲聊。
 * <p>
 * 阶段一用规则关键词分类，足够覆盖常见导购问法；后续要更强的容错，可换成一次轻量 LLM
 * 分类调用（对应文档的双层 Embedding 语义路由），分类逻辑不变，只换实现。
 */
@Component
public class IntentRouter {

    public Intent route(String message) {
        if (message == null || message.isBlank()) {
            return Intent.CHAT;
        }
        // 下单/交易类
        if (contains(message, "下单", "购买", "买", "结算", "支付", "提交订单", "加入购物车")) {
            return Intent.ORDER;
        }
        // 私有知识问答类（售后/政策/规格等）
        if (contains(message, "售后", "退货", "保修", "规格", "参数", "政策", "多久", "怎么", "材质")) {
            return Intent.QA;
        }
        // 推荐/查询商品类
        if (contains(message, "推荐", "介绍", "有哪些", "找", "想要", "有没有", "哪款", "选", "什么", "便宜")) {
            return Intent.RECOMMEND;
        }
        return Intent.CHAT;
    }

    private boolean contains(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
