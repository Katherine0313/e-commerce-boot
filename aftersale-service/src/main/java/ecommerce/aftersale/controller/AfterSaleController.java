package ecommerce.aftersale.controller;

import ecommerce.aftersale.dto.CreateAfterSaleDTO;
import ecommerce.aftersale.dto.SubmitReturnDTO;
import ecommerce.aftersale.service.AfterSaleService;
import ecommerce.aftersale.vo.AfterSaleStatusConstantsVO;
import ecommerce.aftersale.vo.AfterSaleVO;
import ecommerce.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 售后接口（C 端，需登录）。
 * <p>
 * 后台审核/删除走 /api/admin/after-sales/**（本次未实现，后续再迁移）。
 */
@Tag(name = "售后", description = "售后接口（C 端，需登录）")
@RestController
@RequestMapping("/api/after-sales")
public class AfterSaleController {

    private final AfterSaleService afterSaleService;

    public AfterSaleController(AfterSaleService afterSaleService) {
        this.afterSaleService = afterSaleService;
    }

    @Operation(summary = "申请售后（整单）")
    @PostMapping
    public Result<AfterSaleVO> create(@RequestBody CreateAfterSaleDTO dto) {
        return Result.success(afterSaleService.create(dto));
    }

    @Operation(summary = "我的售后列表")
    @GetMapping
    public Result<List<AfterSaleVO>> list(@RequestParam(required = false) String status) {
        return Result.success(afterSaleService.list(status));
    }

    @Operation(summary = "售后状态常量")
    @GetMapping("/status-constants")
    public Result<AfterSaleStatusConstantsVO> statusConstants() {
        return Result.success(afterSaleService.statusConstants());
    }

    @Operation(summary = "售后详情")
    @GetMapping("/{id}")
    public Result<AfterSaleVO> getById(@PathVariable Long id) {
        return Result.success(afterSaleService.getById(id));
    }

    @Operation(summary = "提交退货方式")
    @PostMapping("/{id}/submit-return")
    public Result<AfterSaleVO> submitReturn(@PathVariable Long id, @RequestBody SubmitReturnDTO dto) {
        return Result.success(afterSaleService.submitReturn(id, dto));
    }
}
