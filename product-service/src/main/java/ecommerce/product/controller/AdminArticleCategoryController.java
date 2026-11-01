package ecommerce.product.controller;

import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.product.dto.AdminArticleCategoryDTO;
import ecommerce.product.service.ArticleCategoryService;
import ecommerce.product.vo.ArticleCategoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台文章分类管理接口（/api/admin/article-categories）。
 * <p>
 * 文章分类不单独建菜单，在「文章管理」页面里维护；权限复用文章权限。
 */
@Tag(name = "后台文章分类管理", description = "文章分类的增删改查（管理员）")
@RestController
@RequirePerm("business:article:list")
@RequestMapping("/api/admin/article-categories")
public class AdminArticleCategoryController {

    private final ArticleCategoryService articleCategoryService;

    public AdminArticleCategoryController(ArticleCategoryService articleCategoryService) {
        this.articleCategoryService = articleCategoryService;
    }

    @Operation(summary = "分类列表（含文章数）")
    @GetMapping
    public Result<List<ArticleCategoryVO>> list() {
        return Result.success(articleCategoryService.list());
    }

    @Operation(summary = "分类详情")
    @GetMapping("/{id}")
    public Result<ArticleCategoryVO> get(@PathVariable Long id) {
        return Result.success(articleCategoryService.get(id));
    }

    @Operation(summary = "新建分类")
    @RequirePerm("business:article:add")
    @PostMapping
    public Result<Long> create(@RequestBody AdminArticleCategoryDTO dto) {
        return Result.success(articleCategoryService.create(dto));
    }

    @Operation(summary = "编辑分类")
    @RequirePerm("business:article:edit")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AdminArticleCategoryDTO dto) {
        articleCategoryService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除分类（分类下还有文章时不允许删）")
    @RequirePerm("business:article:remove")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        articleCategoryService.delete(id);
        return Result.success();
    }
}
