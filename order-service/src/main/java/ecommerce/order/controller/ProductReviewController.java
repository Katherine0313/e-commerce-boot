package ecommerce.order.controller;

import ecommerce.common.PageVO;
import ecommerce.common.Result;
import ecommerce.order.service.ReviewService;
import ecommerce.order.vo.ReviewDetailVO;
import ecommerce.order.vo.ReviewSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端商品评价只读接口（/api/reviews）。
 * <p>
 * 给小程序商品详情页和 PC 商城商品详情页看评价用：
 * <ul>
 *   <li>只返回 status=1 且未删除的评价 —— 后台隐藏/删除一条，这里立刻不显示</li>
 *   <li>不返回订单号、用户 ID，手机号昵称已打码</li>
 *   <li>路径刻意用 /api/reviews/product/{id}，不用 /api/products/{id}/reviews ——
 *       网关的 product-service 路由排在 order-service 前面，带 /api/products 前缀会被抢走</li>
 * </ul>
 * 免登录：在 common 的 WebMvcConfig.EXCLUDE_AUTH_PATHS 里已放行 /api/reviews/**。
 */
@Tag(name = "商品评价（公开）", description = "商品详情页的评价列表与星级统计（免登录只读）")
@RestController
@RequestMapping("/api/reviews")
public class ProductReviewController {

    private final ReviewService reviewService;

    public ProductReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "商品评价列表（分页）")
    @GetMapping("/product/{productId}")
    public Result<PageVO<ReviewDetailVO>> list(@PathVariable Long productId,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(reviewService.listByProduct(productId, page, pageSize));
    }

    @Operation(summary = "商品评价统计（总数/平均分/好评率/星级分布）")
    @GetMapping("/product/{productId}/summary")
    public Result<ReviewSummaryVO> summary(@PathVariable Long productId) {
        return Result.success(reviewService.summary(productId));
    }
}
