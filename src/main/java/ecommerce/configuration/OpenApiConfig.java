package ecommerce.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 接口文档配置。
 * <p>
 * 接口本身由 springdoc 自动扫描 Controller 生成，这里做两件事：
 * 覆盖默认的 "OpenAPI definition / v0" 元信息，以及全局补上公共失败响应（见 {@link #commonFailureResponsesCustomizer()}）。
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
                                + "注意：成功返回 HTTP 200，失败时返回真实 HTTP 状态码（400/401/403/404/500），业务结果以响应体为准。"))
                // 声明令牌鉴权方式。根级 security 表示「全文档默认要带令牌」，
                // 登录接口用 security: [] 单独关掉，见 commonFailureResponsesCustomizer
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("登录接口签发的令牌，按 Authorization: Bearer {token} 放在请求头")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    /** 失败响应统一声明的媒体类型 */
    private static final String APPLICATION_JSON = "application/json";
    /** 失败响应体共用的 schema 名，注册进 components 后由各失败响应引用 */
    private static final String RESULT_VOID = "ResultVoid";
    /** 令牌鉴权方案名，与根级 security 的引用名一致 */
    private static final String BEARER_AUTH = "bearerAuth";

    /**
     * 全局追加公共失败响应，业务接口不用逐个写注解，重复几百遍。
     * <p>
     * 用 {@link GlobalOpenApiCustomizer} 而不是 OpenApiCustomizer：前者对整份文档只跑一次，
     * 后者按分组（GroupedOpenApi）各跑一次，将来分了组会把响应重复注入。
     * <p>
     * 判据是「这个码由谁产出」：只要状态码是 GlobalExceptionHandler 统一产出的，就是公共码，放这里注入；
     * 只有该接口**独有语义**的码才写本地注解。按这个判据，现在没有任何接口需要本地注解。
     * <ul>
     *   <li><b>400</b>：所有接口（handleUnreadable 处理请求体/参数不合法）</li>
     *   <li><b>401 / 403</b>：只有 `/api/**` 需登录的接口，免登录接口（EXCLUDE_AUTH_PATHS）跳过，
     *       同时给它们补上覆盖根级 security 的空数组</li>
     *   <li><b>500</b>：所有接口（handleOther 兜底任何未预料异常）</li>
     *   <li><b>413</b>：只有带 multipart/form-data 请求体、真能上传文件的接口
     *       （handleMaxUploadSize 处理超限），不按这个筛会把 413 塞给登录这种接口</li>
     * </ul>
     * 另外，真要手写 @ApiResponse 声明失败码时，springdoc 会拿方法返回值类型去填响应体
     * （例如把 400 的 data 写成头像 URL），与实际的 Result.error() 结构不符，这里统一纠正。
     */
    @Bean
    public GlobalOpenApiCustomizer commonFailureResponsesCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            // 失败响应体统一引用这份结构，注册一次
            Components components = openApi.getComponents() == null ? new Components() : openApi.getComponents();
            components.addSchemas(RESULT_VOID, resultVoidSchema());
            openApi.setComponents(components);

            openApi.getPaths().forEach((path, item) -> {
                // 与 WebMvcConfig 注册拦截器的规则保持一致：/api/** 需登录，登录入口本身除外
                boolean needAuth = path.startsWith("/api/") && !WebMvcConfig.EXCLUDE_AUTH_PATHS.contains(path);
                item.readOperations().forEach(operation -> {
                    if (operation.getResponses() == null) {
                        operation.setResponses(new ApiResponses());
                    }
                    ApiResponses responses = operation.getResponses();
                    // 用 containsKey 跳过本接口已声明的码，避免覆盖本接口更具体的描述
                    if (!responses.containsKey("400")) {
                        responses.addApiResponse("400", errorResponse("请求参数不合法"));
                    }
                    if (needAuth) {
                        if (!responses.containsKey("401")) {
                            responses.addApiResponse("401", errorResponse("未登录或登录已过期"));
                        }
                        if (!responses.containsKey("403")) {
                            responses.addApiResponse("403", errorResponse("无权访问：令牌身份类型不匹配"));
                        }
                    } else {
                        // 空数组 = 不需要鉴权，覆盖根级 security（Swagger UI 上该接口不显示锁图标）
                        operation.setSecurity(List.of());
                    }
                    if (!responses.containsKey("500")) {
                        responses.addApiResponse("500", errorResponse("服务器内部错误"));
                    }
                    // 大小限制来自 spring.servlet.multipart 配置，文案与 docs/openapi.yaml 的 PayloadTooLarge 保持一致
                    if (isMultipart(operation) && !responses.containsKey("413")) {
                        responses.addApiResponse("413", errorResponse("上传文件超过大小限制（单个文件 2MB，整个请求 3MB）"));
                    }
                    // 失败响应体一律是 Result：GlobalExceptionHandler 返回的都是这个结构，data 恒为 null
                    responses.forEach((status, response) -> {
                        if (isErrorStatus(status)) {
                            response.setContent(resultVoidContent());
                        }
                    });
                });
            });
        };
    }

    /** 是否接收上传文件：只有这种接口才可能返回 413（上传大小限制由 multipart 配置管控） */
    private boolean isMultipart(Operation operation) {
        RequestBody requestBody = operation.getRequestBody();
        return requestBody != null && requestBody.getContent() != null
                && requestBody.getContent().containsKey("multipart/form-data");
    }

    /** 是否为失败状态码；"default" 之类的非数字码不算 */
    private boolean isErrorStatus(String status) {
        try {
            return Integer.parseInt(status) >= 400;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 失败响应：描述 + 统一的 ResultVoid 响应体 */
    private ApiResponse errorResponse(String description) {
        return new ApiResponse().description(description).content(resultVoidContent());
    }

    /** 指向 ResultVoid 的响应体声明 */
    private Content resultVoidContent() {
        return new Content().addMediaType(APPLICATION_JSON,
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + RESULT_VOID)));
    }

    /** 失败响应体结构，与 Result<T> 的实际字段一致（失败时 data 恒为 null） */
    private Schema<?> resultVoidSchema() {
        Schema<?> schema = new Schema<>();
        schema.setType("object");
        schema.setDescription("失败响应体（HTTP 状态码为真实的失败码，不是恒 200）");
        schema.addProperty("success", new Schema<>().type("boolean").description("恒为 false").example(false));
        schema.addProperty("data", new Schema<>().type("object").nullable(true).description("失败时恒为 null"));
        schema.addProperty("message", new Schema<>().type("string").description("错误提示，可直接展示给用户"));
        schema.addProperty("code", new Schema<>().type("string").nullable(true)
                .description("可选字符串错误码（如 UNAUTHORIZED / FORBIDDEN / NOT_FOUND），当前阶段多为 null"));
        return schema;
    }
}
