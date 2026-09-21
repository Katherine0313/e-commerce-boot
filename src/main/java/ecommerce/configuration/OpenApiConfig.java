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
                        .title("电商小程序接口文档")
                        .version("v1.0")
                        .description("微信小程序端接口。除登录接口外，其余 /api/** 接口都需在请求头带 "
                                + "Authorization: Bearer {token}。"
                                + "注意：HTTP 状态码恒为 200，业务结果以响应体中的 code 字段为准。"));
    }
}
