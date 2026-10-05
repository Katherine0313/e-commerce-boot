package ecommerce.aiagent.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 下单工具（Function Calling 写操作，HITL 人在回路）。
 * <p>
 * 阶段三：order-service 下单接口就绪后，这里改为通过 OpenFeign 真实下单；
 * 下单是写操作，务必走「生成订单预览 → 用户确认 → 才调用」的人在回路流程。
 */
@Component
public class OrderTool {

    @Tool(description = "创建订单（写操作，须用户先确认订单预览）。当前 order-service 下单接口未就绪，返回待接入提示。")
    public String placeOrder(
            @ToolParam(description = "商品 id") Long productId,
            @ToolParam(description = "购买数量") int quantity) {
        return "下单功能尚未接入（order-service 下单接口待实现）。当前已完成商品推荐，实际下单请等订单服务就绪。";
    }
}
