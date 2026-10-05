package ecommerce.aiagent.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

/**
 * Embedding 向量化：把文本转成高维向量（RAG 流水线的「向量化」环节）。
 * <p>
 * 底层用硅基流动的 embedding 模型（由 OpenAI starter 的 EmbeddingModel 提供）；
 * 后续换 Milvus/自定义 embedding 时，向量化的入口仍在这里，检索层无感。
 */
@Component
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }
}
