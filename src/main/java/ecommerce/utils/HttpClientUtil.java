package ecommerce.utils;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP 工具类
 */
@Component
public class HttpClientUtil {

    /** RestClient 线程安全，复用同一个实例即可；超时由 spring.http.client.* 配置 */
    private final RestClient restClient;

    public HttpClientUtil(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    /**
     * 发送 GET 请求，返回响应体字符串
     * <p>
     * 这里不直接按对象解析的原因：部分接口（如微信失败时）返回的 Content-Type 是 text/plain，
     * 按对象解析会抛 UnknownContentTypeException。所以统一先读成字符串，由调用方决定怎么解析。
     *
     * @param url 完整请求地址
     * @return 响应体原文
     */
    public String get(String url) {
        return restClient.get()
                .uri(url)
                .exchange((request, response) -> response.bodyTo(String.class));
    }
}
