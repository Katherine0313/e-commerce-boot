package ecommerce.aiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * AI 导购 Agent 服务启动类。
 * <p>
 * 完整 Agent 而非「LLM 聊天壳」：意图路由（Graph 工作流）+ RAG 私有知识检索 + MCP 数据源标准化
 * + Function Calling 工具调用。LLM 引擎走硅基流动 OpenAI 兼容接口（DeepSeek）。
 */
@SpringBootApplication(scanBasePackages = "ecommerce")
@EnableFeignClients(basePackages = "ecommerce.aiagent.feign")
public class AiAgentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAgentServiceApplication.class, args);
    }
}
