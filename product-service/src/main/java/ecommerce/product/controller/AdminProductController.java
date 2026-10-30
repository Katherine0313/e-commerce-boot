package ecommerce.product.controller;

import ecommerce.common.PageVO;
import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.product.dto.AdminProductDTO;
import ecommerce.product.service.AdminProductService;
import ecommerce.product.vo.AdminProductVO;
import ecommerce.product.vo.DashboardVO;
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
 * 后台商品管理接口（/api/admin/products）。
 * <p>
 * 路径 /api/admin/** 由 JwtInterceptor 按 admin 身份分流校验，需 admin 令牌。
 */
@Tag(name = "后台商品管理", description = "商品增删改查（管理员）")
@RestController
@RequirePerm("product:product:list")
@RequestMapping("/api/admin/products")
public class AdminProductController {
    private final AdminProductService adminProductService;

    public AdminProductController(AdminProductService adminProductService) {
        this.adminProductService = adminProductService;
    }

    @Operation(summary = "商品列表（分页 + 关键词/分类筛选）")
    @GetMapping
    public Result<PageVO<AdminProductVO>> list(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int pageSize,
                                               @RequestParam(required = false) String search,
                                               @RequestParam(required = false) Long categoryId) {
        return Result.success(adminProductService.list(page, pageSize, search, categoryId));
    }

    @Operation(summary = "商品详情（含成本价）")
    @GetMapping("/{id}")
    public Result<AdminProductVO> get(@PathVariable Long id) {
        return Result.success(adminProductService.get(id));
    }

    @Operation(summary = "新增商品")
    @RequirePerm("product:product:add")
    @PostMapping
    public Result<AdminProductVO> create(@RequestBody AdminProductDTO dto) {
        return Result.success(adminProductService.create(dto));
    }

    @Operation(summary = "更新商品（局部更新）")
    @RequirePerm("product:product:edit")
    @PutMapping("/{id}")
    public Result<AdminProductVO> update(@PathVariable Long id, @RequestBody AdminProductDTO dto) {
        return Result.success(adminProductService.update(id, dto));
    }

    @Operation(summary = "删除商品")
    @RequirePerm("product:product:remove")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminProductService.delete(id);
        return Result.success();
    }

    @Operation(summary = "品牌列表（去重）")
    @GetMapping("/brands")
    public Result<List<String>> brands() {
        return Result.success(adminProductService.brands());
    }

    @Operation(summary = "工作台统计（商品总数/总库存/库存总价值）")
    @RequirePerm("workplace:view")
    @GetMapping("/dashboard")
    public Result<DashboardVO> dashboard() {
        return Result.success(adminProductService.dashboard());
    }
}
