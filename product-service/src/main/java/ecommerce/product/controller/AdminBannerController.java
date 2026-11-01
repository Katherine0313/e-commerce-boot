package ecommerce.product.controller;

import ecommerce.common.PageVO;
import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.product.dto.AdminBannerDTO;
import ecommerce.product.service.AdminBannerService;
import ecommerce.product.vo.BannerVO;
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
 * 后台 Banner 管理接口（/api/admin/banners）。
 */
@Tag(name = "后台 Banner 管理", description = "轮播广告的增删改查与启用禁用")
@RestController
@RequirePerm("business:banner:list")
@RequestMapping("/api/admin/banners")
public class AdminBannerController {

    private final AdminBannerService adminBannerService;

    public AdminBannerController(AdminBannerService adminBannerService) {
        this.adminBannerService = adminBannerService;
    }

    @Operation(summary = "Banner 列表（分页 + 标题/位置/状态筛选）")
    @GetMapping
    public Result<PageVO<BannerVO>> list(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int pageSize,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Integer position,
                                          @RequestParam(required = false) Integer status) {
        return Result.success(adminBannerService.list(page, pageSize, keyword, position, status));
    }

    @Operation(summary = "Banner 详情")
    @GetMapping("/{id}")
    public Result<BannerVO> get(@PathVariable Long id) {
        return Result.success(adminBannerService.get(id));
    }

    @Operation(summary = "新建 Banner")
    @RequirePerm("business:banner:add")
    @PostMapping
    public Result<Long> create(@RequestBody AdminBannerDTO dto) {
        return Result.success(adminBannerService.create(dto));
    }

    @Operation(summary = "编辑 Banner")
    @RequirePerm("business:banner:edit")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AdminBannerDTO dto) {
        adminBannerService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "启用 / 禁用 Banner")
    @RequirePerm("business:banner:edit")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminBannerService.updateStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "删除 Banner（逻辑删除）")
    @RequirePerm("business:banner:remove")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminBannerService.delete(id);
        return Result.success();
    }
}
