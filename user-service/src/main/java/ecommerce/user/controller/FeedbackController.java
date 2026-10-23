package ecommerce.user.controller;

import ecommerce.common.Result;
import ecommerce.user.dto.CreateFeedbackDTO;
import ecommerce.user.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 意见反馈接口（C 端，需登录）。
 * <p>
 * /api/feedback 不在 common 的 EXCLUDE_AUTH_PATHS 白名单内，JwtInterceptor 自动要求 user 令牌；
 * 网关兜底 /api/** → user-service，无需新增路由。
 */
@Tag(name = "意见反馈", description = "C 端意见反馈提交（需登录）")
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @Operation(summary = "提交意见反馈")
    @PostMapping
    public Result<Long> submit(@RequestBody CreateFeedbackDTO dto) {
        return Result.success(feedbackService.submit(dto));
    }
}
