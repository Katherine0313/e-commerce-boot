package ecommerce.order.controller;

import ecommerce.common.Result;
import ecommerce.order.dto.CreateReviewDTO;
import ecommerce.order.service.ReviewService;
import ecommerce.order.vo.ReviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品评价接口（需登录）。
 * <p>
 * 挂在订单路径下（/api/orders/{id}/review），复用网关已配置的 order-service 路由，无需新增网关规则。
 */
@Tag(name = "评价", description = "商品评价接口（需登录）")
@RestController
@RequestMapping("/api/orders")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "提交评价（订单已完成且未评价）")
    @PostMapping("/{id}/review")
    public Result<ReviewVO> submit(@PathVariable Long id, @RequestBody CreateReviewDTO dto) {
        return Result.success(reviewService.submit(id, dto));
    }
}
