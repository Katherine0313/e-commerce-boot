package ecommerce.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档元信息配置：设置 Swagger UI 顶部展示的标题、版本和说明
 * <p>
 * 接口本身由 springdoc 自动扫描 Controller 生成，这里只覆盖默认的 "OpenAPI definition / v0"。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ecommerceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("电商接口文档")
                        .version("v1.0")
                        .description("小程序端 + PC 端商城 + 后台管理系统共用一个后端。除登录接口外，其余接口都需在请求头带 "
                                + "Authorization: Bearer {token}。"
                                + "注意：成功返回 HTTP 200，失败时返回真实 HTTP 状态码（400/401/403/404/500），业务结果以响应体为准。"));
    }
}
