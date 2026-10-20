package ecommerce.aftersale.controller;

import ecommerce.aftersale.dto.AdminAfterSaleDTO;
import ecommerce.aftersale.service.AdminAfterSaleService;
import ecommerce.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台售后管理接口（/api/admin/after-sales）。
 * <p>
 * 路径 /api/admin/** 由 JwtInterceptor 按 admin 身份分流校验，需 admin 令牌。
 */
@Tag(name = "后台售后管理", description = "售后审核/删除（管理员）")
@RestController
@RequestMapping("/api/admin/after-sales")
public class AdminAfterSaleController {
    private final AdminAfterSaleService adminAfterSaleService;

    public AdminAfterSaleController(AdminAfterSaleService adminAfterSaleService) {
        this.adminAfterSaleService = adminAfterSaleService;
    }

    @Operation(summary = "审核售后（更新状态 + 子表时间戳 + 商家备注）")
    @PutMapping("/{id}")
    public Result<Void> review(@PathVariable Long id, @RequestBody AdminAfterSaleDTO dto) {
        adminAfterSaleService.review(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除售后（软删主表/明细/子表）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminAfterSaleService.delete(id);
        return Result.success();
    }
}
