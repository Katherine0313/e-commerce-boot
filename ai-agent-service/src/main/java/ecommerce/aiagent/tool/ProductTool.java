package ecommerce.aiagent.tool;

import ecommerce.aiagent.feign.ProductClient;
import ecommerce.aiagent.feign.ProductVO;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商品查询工具（Function Calling）：大模型在推荐 / 查询 / 比较商品时调用，通过 OpenFeign 查 product-service。
 */
@Component
public class ProductTool {

    private final ProductClient productClient;

    public ProductTool(ProductClient productClient) {
        this.productClient = productClient;
    }

    @Tool(description = "查询在售商品列表，返回商品 id、名称、副标题、品牌、价格、描述。当用户要推荐商品、比较商品、查找某类商品时调用。")
    public List<ProductVO> searchProducts(
            @ToolParam(description = "用户想要的商品关键词或需求，如「键盘」「送给程序员的礼物」") String query) {
        // 阶段一：拉全量在售商品交给大模型筛选；后续可在 product-service 侧按 categoryId/keyword 过滤
        return productClient.listProducts().getData();
    }

    @Tool(description = "根据商品 id 查询单个商品详情。当用户想了解某个具体商品时调用。")
    public ProductVO getProduct(@ToolParam(description = "商品 id") Long id) {
        return productClient.getProduct(id).getData();
    }
}
