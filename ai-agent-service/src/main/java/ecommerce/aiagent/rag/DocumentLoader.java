package ecommerce.aiagent.rag;

import ecommerce.aiagent.feign.ProductClient;
import ecommerce.aiagent.feign.ProductVO;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 商品文档加载：从 product-service 拉商品，生成 RAG 用的文档（商品名 + 品牌 + 价格 + 描述）。
 * <p>
 * 这是 RAG 数据摄入流水线的「源数据读取」环节；后续可扩展为读商品详情、售后政策等文档。
 */
@Component
public class DocumentLoader {

    private final ProductClient productClient;

    public DocumentLoader(ProductClient productClient) {
        this.productClient = productClient;
    }

    public List<Document> loadProductDocuments() {
        List<ProductVO> products = productClient.listProducts().getData();
        if (products == null || products.isEmpty()) {
            return List.of();
        }
        List<Document> documents = new ArrayList<>(products.size());
        for (ProductVO p : products) {
            StringBuilder content = new StringBuilder();
            content.append("商品：").append(p.getName());
            if (p.getSubTitle() != null && !p.getSubTitle().isBlank()) {
                content.append("（").append(p.getSubTitle()).append("）");
            }
            content.append("；品牌：").append(p.getBrand() == null ? "未知" : p.getBrand());
            content.append("；价格：").append(p.getPrice() == null ? "未知" : p.getPrice());
            if (p.getDescription() != null && !p.getDescription().isBlank()) {
                content.append("；描述：").append(p.getDescription());
            }
            Map<String, Object> metadata = Map.of(
                    "productId", p.getId() == null ? "" : p.getId(),
                    "name", p.getName() == null ? "" : p.getName());
            documents.add(new Document(content.toString(), metadata));
        }
        return documents;
    }
}
