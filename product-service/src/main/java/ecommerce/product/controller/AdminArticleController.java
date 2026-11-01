package ecommerce.product.controller;

import ecommerce.common.PageVO;
import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.product.dto.AdminArticleDTO;
import ecommerce.product.service.AdminArticleService;
import ecommerce.product.vo.ArticleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台文章管理接口（/api/admin/articles）。
 */
@Tag(name = "后台文章管理", description = "文章（关于我们/隐私政策等）的增删改查与上下架")
@RestController
@RequirePerm("business:article:list")
@RequestMapping("/api/admin/articles")
public class AdminArticleController {

    private final AdminArticleService adminArticleService;

    public AdminArticleController(AdminArticleService adminArticleService) {
        this.adminArticleService = adminArticleService;
    }

    @Operation(summary = "文章列表（分页 + 标题/分类/状态筛选）")
    @GetMapping
    public Result<PageVO<ArticleVO>> list(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int pageSize,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) Long categoryId,
                                           @RequestParam(required = false) Integer status) {
        return Result.success(adminArticleService.list(page, pageSize, keyword, categoryId, status));
    }

    @Operation(summary = "文章详情（含正文）")
    @GetMapping("/{id}")
    public Result<ArticleVO> get(@PathVariable Long id) {
        return Result.success(adminArticleService.get(id));
    }

    @Operation(summary = "新建文章")
    @RequirePerm("business:article:add")
    @PostMapping
    public Result<Long> create(@RequestBody AdminArticleDTO dto) {
        return Result.success(adminArticleService.create(dto));
    }

    @Operation(summary = "编辑文章")
    @RequirePerm("business:article:edit")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AdminArticleDTO dto) {
        adminArticleService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "启用 / 禁用文章")
    @RequirePerm("business:article:edit")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminArticleService.updateStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "删除文章（逻辑删除）")
    @RequirePerm("business:article:remove")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminArticleService.delete(id);
        return Result.success();
    }
}
