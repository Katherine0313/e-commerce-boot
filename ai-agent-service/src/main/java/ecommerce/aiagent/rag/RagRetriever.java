package ecommerce.aiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 检索：文档入库 + Top-K 相似检索，把命中片段拼成上下文交给大模型（降低幻觉）。
 * <p>
 * 开发用 SimpleVectorStore（内存/文件，零外部部署），生产换 Milvus/PgVector 时只改 VectorStore 的 Bean 装配。
 */
@Component
public class RagRetriever {

    private final VectorStore vectorStore;

    public RagRetriever(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** 商品文档入库（向量化 + 存向量库） */
    public void indexDocuments(List<Document> documents) {
        if (documents != null && !documents.isEmpty()) {
            vectorStore.add(documents);
        }
    }

    /** 按 query 检索 Top-K 相关文档，拼接为一段上下文文本 */
    public String retrieve(String query, int topK) {
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build());
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        return hits.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));
    }
}
