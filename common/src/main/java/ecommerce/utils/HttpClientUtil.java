package ecommerce.utils;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
public class HttpClientUtil {
    private final RestClient restClient;

    public HttpClientUtil(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    // 发起GET请求（无参数，走 doGet 统一逻辑）
    public String get(String url){
        return doGet(url, null);
    }

    // 发起GET请求，参数以 Map 传入，由工具类统一做 URL 编码后拼接
    public String doGet(String url, Map<String, String> params){
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        if (params != null) {
            params.forEach(builder::queryParam);
        }
        return restClient.get()
                .uri(builder.build().encode().toUri())
                .exchange((request, response) -> response.bodyTo(String.class));
    }

    // 发起 POST 请求，body 为 JSON 字符串（Content-Type: application/json），用于调微信「手机号」等 JSON 入参接口
    public String doPostJson(String url, String jsonBody){
        return restClient.post()
                .uri(UriComponentsBuilder.fromUriString(url).build().encode().toUri())
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBody)
                .exchange((request, response) -> response.bodyTo(String.class));
    }
}
