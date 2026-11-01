package ecommerce.product.controller;

import ecommerce.common.Result;
import ecommerce.product.service.ArticleService;
import ecommerce.product.vo.ArticleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文章浏览接口（PC 商城文章详情页 / 小程序富文本页，免登录）。
 * <p>
 * 这些接口在 common 的 WebMvcConfig.EXCLUDE_AUTH_PATHS 白名单内，不带 token 也能访问。
 */
@Tag(name = "文章浏览", description = "文章详情查询与浏览量统计（免登录）")
@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @Operation(summary = "文章详情（按 ID）")
    @GetMapping("/{id}")
    public Result<ArticleVO> get(@PathVariable Long id) {
        return Result.success(articleService.getById(id));
    }

    @Operation(summary = "文章详情（按标题，小程序富文本页用）")
    @GetMapping("/by-title")
    public Result<ArticleVO> getByTitle(@RequestParam String title) {
        return Result.success(articleService.getByTitle(title));
    }

    @Operation(summary = "文章列表（可按热门 / 轮播过滤）")
    @GetMapping
    public Result<List<ArticleVO>> list(@RequestParam(required = false) Integer recommendHot,
                                        @RequestParam(required = false) Integer recommendBanner,
                                        @RequestParam(required = false) Integer limit) {
        return Result.success(articleService.listEnabled(recommendHot, recommendBanner, limit));
    }

    @Operation(summary = "文章浏览量 +1")
    @PostMapping("/{id}/browse")
    public Result<Void> browse(@PathVariable Long id) {
        articleService.browse(id);
        return Result.success();
    }
}
