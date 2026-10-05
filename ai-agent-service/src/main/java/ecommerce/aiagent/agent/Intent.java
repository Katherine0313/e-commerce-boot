package ecommerce.aiagent.agent;

/**
 * 意图枚举：对应文档「双层 Embedding 语义路由」的粗分类（流程类/工具类/推荐类/闲聊类）。
 */
public enum Intent {
    /** 推荐 / 查询商品（走 Function Calling 查 product-service） */
    RECOMMEND,
    /** 下单 / 交易（走 OrderTool，HITL） */
    ORDER,
    /** 私有知识问答（走 RAG 检索商品文档） */
    QA,
    /** 闲聊 / 其它 */
    CHAT
}
