package ecommerce.product.controller;

import ecommerce.common.Result;
import ecommerce.product.dto.AdminTagDTO;
import ecommerce.product.dto.TagProductsDTO;
import ecommerce.product.entity.Product;
import ecommerce.product.service.AdminTagService;
import ecommerce.product.vo.TagVO;
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
 * 后台标签管理接口（/api/admin/tags）。
 */
@Tag(name = "后台标签管理", description = "标签增删改查 + 商品绑定（管理员）")
@RestController
@RequestMapping("/api/admin/tags")
public class AdminTagController {
    private final AdminTagService adminTagService;

    public AdminTagController(AdminTagService adminTagService) {
        this.adminTagService = adminTagService;
    }

    @Operation(summary = "标签列表")
    @GetMapping
    public Result<List<TagVO>> list() {
        return Result.success(adminTagService.list());
    }

    @Operation(summary = "新增标签")
    @PostMapping
    public Result<TagVO> create(@RequestBody AdminTagDTO dto) {
        return Result.success(adminTagService.create(dto));
    }

    @Operation(summary = "更新标签（局部更新）")
    @PutMapping("/{id}")
    public Result<TagVO> update(@PathVariable Long id, @RequestBody AdminTagDTO dto) {
        return Result.success(adminTagService.update(id, dto));
    }

    @Operation(summary = "删除标签")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminTagService.delete(id);
        return Result.success();
    }

    @Operation(summary = "标签关联的商品列表")
    @GetMapping("/{tagId}/products")
    public Result<List<Product>> listTagProducts(@PathVariable Long tagId) {
        return Result.success(adminTagService.listTagProducts(tagId));
    }

    @Operation(summary = "给一批商品打上标签")
    @PostMapping("/{tagId}/products")
    public Result<Void> bindProductsToTag(@PathVariable Long tagId, @RequestBody TagProductsDTO dto) {
        adminTagService.bindProductsToTag(tagId, dto.getProductIds());
        return Result.success();
    }

    @Operation(summary = "批量解绑标签下的商品")
    @DeleteMapping("/{tagId}/products")
    public Result<Void> unbindTagProducts(@PathVariable Long tagId, @RequestBody TagProductsDTO dto) {
        adminTagService.unbindTagProducts(tagId, dto.getProductIds());
        return Result.success();
    }

    @Operation(summary = "解绑单个商品的标签")
    @DeleteMapping("/products/{productId}/tags/{tagId}")
    public Result<Void> unbindProductTag(@PathVariable Long productId, @PathVariable Long tagId) {
        adminTagService.unbindProductTag(productId, tagId);
        return Result.success();
    }
}
