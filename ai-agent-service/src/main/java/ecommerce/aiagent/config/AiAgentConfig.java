package ecommerce.aiagent.config;

import ecommerce.aiagent.rag.DocumentLoader;
import ecommerce.aiagent.rag.RagRetriever;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI Agent 核心 Bean 装配：ChatClient（LLM 编排）+ VectorStore（RAG）+ 商品文档入库。
 */
@Configuration
public class AiAgentConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("你是「杰米小店」的电商导购助手，能推荐商品、解答商品与售后问题。"
                        + "回答简洁友好；推荐商品时给出名称、价格和推荐理由。")
                .build();
    }

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        // 开发用 SimpleVectorStore（内存起步，零外部部署）；生产换 Milvus/PgVector 只改这里
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    /** 启动后把商品文档入库（RAG 数据摄入；product-service 未就绪时容错跳过，不阻塞启动） */
    @Bean
    public CommandLineRunner indexProductDocuments(DocumentLoader loader, RagRetriever retriever) {
        return args -> {
            try {
                retriever.indexDocuments(loader.loadProductDocuments());
            } catch (Exception ignored) {
                // 下游商品服务可能尚未就绪，跳过入库，后续触发补
            }
        };
    }
}
