package ecommerce.aftersale.controller;

import ecommerce.aftersale.dto.AdminAfterSaleDTO;
import ecommerce.aftersale.service.AdminAfterSaleService;
import ecommerce.aftersale.vo.AfterSaleLogVO;
import ecommerce.aftersale.vo.AfterSaleVO;
import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台售后管理接口（/api/admin/after-sales）。
 * <p>
 * 路径 /api/admin/** 由 JwtInterceptor 按 admin 身份分流校验，需 admin 令牌。
 */
@Tag(name = "后台售后管理", description = "售后列表/审核/删除（管理员）")
@RestController
@RequirePerm("aftersale:aftersale:list")
@RequestMapping("/api/admin/after-sales")
public class AdminAfterSaleController {
    private final AdminAfterSaleService adminAfterSaleService;

    public AdminAfterSaleController(AdminAfterSaleService adminAfterSaleService) {
        this.adminAfterSaleService = adminAfterSaleService;
    }

    @Operation(summary = "售后列表（状态/关键词/时间范围筛选）")
    @GetMapping
    public Result<List<AfterSaleVO>> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String search,
                                          @RequestParam(required = false) String startDate,
                                          @RequestParam(required = false) String endDate,
                                          @RequestParam(required = false) String dateField) {
        return Result.success(adminAfterSaleService.list(status, search, startDate, endDate, dateField));
    }

    @Operation(summary = "售后详情（含明细 + 子表平铺字段 + 可执行动作）")
    @GetMapping("/{id}")
    public Result<AfterSaleVO> getById(@PathVariable Long id) {
        return Result.success(adminAfterSaleService.getById(id));
    }

    @Operation(summary = "售后变更流水（含商家内部备注）")
    @GetMapping("/{id}/logs")
    public Result<List<AfterSaleLogVO>> listLogs(@PathVariable Long id) {
        return Result.success(adminAfterSaleService.listLogs(id));
    }

    @Operation(summary = "审核售后（更新状态 + 子表时间戳 + 商家备注）")
    @RequirePerm("aftersale:aftersale:audit")
    @PutMapping("/{id}")
    public Result<Void> review(@PathVariable Long id, @RequestBody AdminAfterSaleDTO dto) {
        adminAfterSaleService.review(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除售后（软删主表/明细/子表）")
    @RequirePerm("aftersale:aftersale:audit")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminAfterSaleService.delete(id);
        return Result.success();
    }
}
