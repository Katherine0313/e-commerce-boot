package ecommerce.gateway.config;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 网关跨域配置（WebFlux 版）。
 * <p>
 * gateway 是响应式应用，不能用 common 里 Servlet 版的 CorsFilter（且 gateway 的 pom 刻意不引 common，
 * 避免带入 Servlet 容器与 WebFlux 冲突），所以这里用 reactive 的 CorsWebFilter 拦截预检请求并回 CORS 头。
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 开发阶段放开所有来源，生产环境请收紧为具体域名
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

    /**
     * 去重 CORS 响应头。
     * <p>
     * gateway 的 CorsWebFilter 会加一次 CORS 头，转发到下游后 common 的 CorsFilter 又会加一次，
     * 两个 Access-Control-Allow-Origin 合并成多值会被浏览器拒绝（浏览器只允许一个值），
     * 所以这里在响应返回前统一只保留第一个。
     */
    @Bean
    public GlobalFilter dedupeCorsHeaders() {
        return (exchange, chain) -> chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {
                    HttpHeaders headers = exchange.getResponse().getHeaders();
                    dedupe(headers, HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
                    dedupe(headers, HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS);
                    dedupe(headers, HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS);
                    dedupe(headers, HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS);
                }));
    }

    private void dedupe(HttpHeaders headers, String name) {
        List<String> values = headers.get(name);
        if (values != null && values.size() > 1) {
            headers.set(name, values.get(0));
        }
    }
}
