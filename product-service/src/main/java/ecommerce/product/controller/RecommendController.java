package ecommerce.product.controller;

import ecommerce.common.Result;
import ecommerce.product.service.RecommendService;
import ecommerce.product.vo.ProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "商品推荐", description = "猜你喜欢：根据下单记录推荐（免登录，未登录走销量热门）")
@RestController
@RequestMapping("/api")
public class RecommendController {

    private final RecommendService recommendService;

    public RecommendController(RecommendService recommendService) {
        this.recommendService = recommendService;
    }

    @Operation(summary = "猜你喜欢推荐列表")
    @GetMapping("/products/recommend")
    public Result<List<ProductVO>> recommend(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(recommendService.recommend(limit));
    }
}
