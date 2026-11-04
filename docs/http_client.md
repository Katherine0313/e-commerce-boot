# HttpClient 归纳

## 核心概念（先说清楚）

**HttpClient 是【后端 Java 程序】主动向外发送 HTTP 请求，去调用「别的后端服务 / 第三方接口」的工具。**

- 请求方向：**后端 Java → 别的服务 / 第三方接口**（如微信 API）。
- 完整链路：`前端 → 后端 →（HttpClient）→ 微信 `。


## 依赖

不需要引入第三方 HTTP 库（没引 Apache HttpClient / OkHttp），用的是 Spring 官方自带的 `RestClient`。

在 `common/pom.xml`：

```xml
<!-- HTTP 客户端：HttpClientUtil 注入 RestClient.Builder -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-restclient</artifactId>
</dependency>
```

```java
@Component
public class HttpClientUtil {
    private final RestClient restClient;

    public HttpClientUtil(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    // GET（无参）
    public String get(String url) { return doGet(url, null); }

    // GET + query 参数（Map 传入，自动 URL 编码拼接）
    public String doGet(String url, Map<String, String> params) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(url);
        if (params != null) params.forEach(b::queryParam);
        return restClient.get().uri(b.build().encode().toUri())
                .exchange((request, response) -> response.bodyTo(String.class));
    }

    // POST JSON（Content-Type: application/json）
    public String doPostJson(String url, String jsonBody) {
        return restClient.post()
                .uri(UriComponentsBuilder.fromUriString(url).build().encode().toUri())
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBody)
                .exchange((request, response) -> response.bodyTo(String.class));
    }
}
```

## 项目里的两处实际用途

### 1. 调微信官方接口（user-service）

登录、手机号这些能力，只有微信服务器能给出 openid / 手机号，后端必须主动去请求微信：

```java
// UserServiceImpl.java:64  登录：code 换 openid
String respBody = httpClientUtil.doGet("https://api.weixin.qq.com/sns/jscode2session", map);

// :126  拿 access_token（手机号等接口要用）
String respBody = httpClientUtil.doGet("https://api.weixin.qq.com/cgi-bin/token", params);

// :100  手机号授权：code 换手机号（POST JSON）
String respBody = httpClientUtil.doPostJson("https://api.weixin.qq.com/wxa/business/getuserphonenumber...", ...);
```


