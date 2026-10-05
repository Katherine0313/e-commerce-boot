package ecommerce.aiagent.feign;

import ecommerce.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * OpenFeign 调 product-service 的商品浏览接口（lb:// 负载均衡走 Nacos 服务发现）。
 */
@FeignClient(name = "product-service")
public interface ProductClient {

    @GetMapping("/api/products")
    Result<List<ProductVO>> listProducts();

    @GetMapping("/api/products/{id}")
    Result<ProductVO> getProduct(@PathVariable("id") Long id);
}
