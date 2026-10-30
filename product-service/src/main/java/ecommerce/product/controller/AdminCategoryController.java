package ecommerce.product.controller;

import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.product.dto.AdminCategoryDTO;
import ecommerce.product.service.AdminCategoryService;
import ecommerce.product.vo.CategoryTreeVO;
import ecommerce.product.vo.CategoryVO;
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
import java.util.Map;

/**
 * 后台分类管理接口（/api/admin/categories）。
 */
@Tag(name = "后台分类管理", description = "分类增删改查 + 同级排序（管理员）")
@RestController
@RequirePerm("product:category:list")
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {
    private final AdminCategoryService adminCategoryService;

    public AdminCategoryController(AdminCategoryService adminCategoryService) {
        this.adminCategoryService = adminCategoryService;
    }

    @Operation(summary = "分类树（一级父分类含 children）")
    @GetMapping
    public Result<List<CategoryTreeVO>> listTree() {
        return Result.success(adminCategoryService.listTree());
    }

    @Operation(summary = "分类扁平列表")
    @GetMapping("/flat")
    public Result<List<CategoryVO>> listFlat() {
        return Result.success(adminCategoryService.listFlat());
    }

    @Operation(summary = "新增分类")
    @RequirePerm("product:category:add")
    @PostMapping
    public Result<CategoryVO> create(@RequestBody AdminCategoryDTO dto) {
        return Result.success(adminCategoryService.create(dto));
    }

    @Operation(summary = "更新分类（局部更新）")
    @RequirePerm("product:category:edit")
    @PutMapping("/{id}")
    public Result<CategoryVO> update(@PathVariable Long id, @RequestBody AdminCategoryDTO dto) {
        return Result.success(adminCategoryService.update(id, dto));
    }

    @Operation(summary = "删除分类")
    @RequirePerm("product:category:remove")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminCategoryService.delete(id);
        return Result.success();
    }

    @Operation(summary = "同级上移/下移")
    @RequirePerm("product:category:edit")
    @PostMapping("/{id}/move")
    public Result<Void> move(@PathVariable Long id, @RequestBody Map<String, String> body) {
        adminCategoryService.move(id, body.get("direction"));
        return Result.success();
    }
}
