package ecommerce.aiagent.mcp;

import ecommerce.aiagent.feign.ProductVO;
import ecommerce.aiagent.tool.OrderTool;
import ecommerce.aiagent.tool.ProductTool;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MCP 数据源标准化：把商品/订单工具用 @McpTool 暴露为 MCP server（默认 /mcp 端点）。
 * <p>
 * 工具逻辑只写一份（{@link ProductTool} / {@link OrderTool}），@Tool 供内部 Function Calling，
 * @McpTool 供外部 MCP 客户端标准化接入；新增业务数据源不改 Agent 核心代码，只在这里加暴露。
 */
@Configuration
public class McpServerConfig {

    @Bean
    public McpCommerceTools mcpCommerceTools(ProductTool productTool, OrderTool orderTool) {
        return new McpCommerceTools(productTool, orderTool);
    }

    public static class McpCommerceTools {

        private final ProductTool productTool;
        private final OrderTool orderTool;

        public McpCommerceTools(ProductTool productTool, OrderTool orderTool) {
            this.productTool = productTool;
            this.orderTool = orderTool;
        }

        @McpTool(description = "查询在售商品列表，返回商品 id、名称、品牌、价格、描述")
        public List<ProductVO> searchProducts(@McpToolParam(description = "商品关键词或需求") String query) {
            return productTool.searchProducts(query);
        }

        @McpTool(description = "创建订单（写操作，须用户确认后执行）")
        public String placeOrder(
                @McpToolParam(description = "商品 id") Long productId,
                @McpToolParam(description = "购买数量") int quantity) {
            return orderTool.placeOrder(productId, quantity);
        }
    }
}
