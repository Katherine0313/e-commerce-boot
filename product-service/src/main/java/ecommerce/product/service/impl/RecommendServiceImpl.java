package ecommerce.product.service.impl;

import ecommerce.product.dto.RecommendResponseDTO;
import ecommerce.product.service.ProductService;
import ecommerce.product.service.RecommendService;
import ecommerce.product.vo.ProductVO;
import ecommerce.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 推荐 Service 实现。
 * <p>
 * 流程：取当前用户（未登录为 null）→ 调 Python 推荐服务拿商品 ID → 查商品表拼 VO。
 * Python 超时/异常时兜底返回商品列表前 N 个，保证首页不白屏。
 */
@Slf4j
@Service
public class RecommendServiceImpl implements RecommendService {

    private final RestClient restClient;
    private final ProductService productService;

    public RecommendServiceImpl(
            RestClient.Builder builder,
            ProductService productService,
            @Value("${recommend.base-url}") String baseUrl,
            @Value("${recommend.connect-timeout:500}") long connectTimeout,
            @Value("${recommend.read-timeout:1000}") long readTimeout) {
        this.productService = productService;
        // 给 Python 调用单独设超时，避免慢调用拖垮商品浏览
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeout));
        factory.setReadTimeout(Duration.ofMillis(readTimeout));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public List<ProductVO> recommend(Integer limit) {
        Long userId = UserContext.getUserId();
        List<Long> ids = fetchRecommendIds(userId, limit);
        if (ids.isEmpty()) {
            return fallback(limit);
        }
        // listProductsByIds 不保证顺序，按 Python 返回顺序重排
        Map<Long, ProductVO> voMap = productService.listProductsByIds(ids).stream()
                .collect(Collectors.toMap(ProductVO::getId, Function.identity(), (a, b) -> a));
        return ids.stream()
                .map(voMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /** 调 Python 拿推荐商品 ID；任何异常/空结果返回空列表，由调用方兜底 */
    private List<Long> fetchRecommendIds(Long userId, Integer limit) {
        try {
            RecommendResponseDTO resp = restClient.get()
                    .uri(uriBuilder -> {
                        UriBuilder b = uriBuilder.path("/recommend").queryParam("limit", limit);
                        if (userId != null) {
                            b = b.queryParam("user_id", userId);
                        }
                        return b.build();
                    })
                    .retrieve()
                    .body(RecommendResponseDTO.class);
            return resp == null || resp.getProductIds() == null ? List.of() : resp.getProductIds();
        } catch (Exception e) {
            log.warn("调用推荐服务失败，走兜底: {}", e.getMessage());
            return List.of();
        }
    }

    /** 兜底：Python 不可用时返回商品列表前 N 个 */
    private List<ProductVO> fallback(Integer limit) {
        List<ProductVO> all = productService.listProducts();
        return all.size() > limit ? all.subList(0, limit) : all;
    }
}
