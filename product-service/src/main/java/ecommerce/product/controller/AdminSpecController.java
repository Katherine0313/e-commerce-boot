package ecommerce.product.controller;

import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.product.dto.SpecGroupDTO;
import ecommerce.product.dto.SpecValueDTO;
import ecommerce.product.service.SpecCatalogService;
import ecommerce.product.vo.SpecGroupVO;
import ecommerce.product.vo.SpecValueVO;
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

import java.util.List;

/**
 * 后台销售规格库（/api/admin/specs）。
 * <p>
 * 复用商品管理权限，避免引入新的权限码导致存量管理员角色缺权限。
 */
@Tag(name = "后台规格库", description = "销售规格名与候选值维护（管理员）")
@RestController
@RequirePerm("product:product:list")
@RequestMapping("/api/admin/specs")
public class AdminSpecController {

    private final SpecCatalogService specCatalogService;

    public AdminSpecController(SpecCatalogService specCatalogService) {
        this.specCatalogService = specCatalogService;
    }

    @Operation(summary = "规格库列表（规格名 + 候选值）")
    @GetMapping
    public Result<List<SpecGroupVO>> list(
            @RequestParam(value = "categoryId", required = false) Long categoryId) {
        return Result.success(specCatalogService.list(categoryId));
    }

    @Operation(summary = "新增规格名")
    @RequirePerm("product:product:add")
    @PostMapping("/groups")
    public Result<SpecGroupVO> createGroup(@RequestBody SpecGroupDTO dto) {
        return Result.success(specCatalogService.createGroup(dto));
    }

    @Operation(summary = "修改规格名/启停")
    @RequirePerm("product:product:edit")
    @PutMapping("/groups/{id}")
    public Result<SpecGroupVO> updateGroup(@PathVariable Long id, @RequestBody SpecGroupDTO dto) {
        return Result.success(specCatalogService.updateGroup(id, dto));
    }

    @Operation(summary = "删除规格名（被商品引用时禁止）")
    @RequirePerm("product:product:remove")
    @DeleteMapping("/groups/{id}")
    public Result<Void> deleteGroup(@PathVariable Long id) {
        specCatalogService.deleteGroup(id);
        return Result.success();
    }

    @Operation(summary = "新增规格值")
    @RequirePerm("product:product:add")
    @PostMapping("/values")
    public Result<SpecValueVO> addValue(@RequestBody SpecValueDTO dto) {
        return Result.success(specCatalogService.addValue(dto));
    }

    @Operation(summary = "删除规格值")
    @RequirePerm("product:product:remove")
    @DeleteMapping("/values/{id}")
    public Result<Void> deleteValue(@PathVariable Long id) {
        specCatalogService.deleteValue(id);
        return Result.success();
    }
}
