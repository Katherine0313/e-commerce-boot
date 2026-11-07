package ecommerce.aftersale.controller;

import ecommerce.aftersale.dto.CreateAfterSaleDTO;
import ecommerce.aftersale.dto.SubmitReturnDTO;
import ecommerce.aftersale.service.AfterSaleService;
import ecommerce.aftersale.vo.AfterSaleCountsVO;
import ecommerce.aftersale.vo.AfterSaleLogVO;
import ecommerce.aftersale.vo.AfterSaleStatusConstantsVO;
import ecommerce.aftersale.vo.AfterSaleVO;
import ecommerce.common.PageVO;
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

    @Operation(summary = "我的售后列表（状态/关键词/时间范围筛选）")
    @GetMapping
    public Result<List<AfterSaleVO>> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String search,
                                          @RequestParam(required = false) String startDate,
                                          @RequestParam(required = false) String endDate,
                                          @RequestParam(required = false) String dateField) {
        return Result.success(afterSaleService.list(status, search, startDate, endDate, dateField));
    }

    @Operation(summary = "我的售后分页列表（状态/关键词/时间范围筛选）")
    @GetMapping("/page")
    public Result<PageVO<AfterSaleVO>> page(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String search,
                                            @RequestParam(required = false) String startDate,
                                            @RequestParam(required = false) String endDate,
                                            @RequestParam(required = false) String dateField,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(afterSaleService.page(status, search, startDate, endDate, dateField, page, pageSize));
    }

    @Operation(summary = "我的售后各状态数量")
    @GetMapping("/counts")
    public Result<AfterSaleCountsVO> counts() {
        return Result.success(afterSaleService.counts());
    }

    @Operation(summary = "售后状态常量")
    @GetMapping("/status-constants")
    public Result<AfterSaleStatusConstantsVO> statusConstants() {
        return Result.success(afterSaleService.statusConstants());
    }

    @Operation(summary = "按订单查售后单（判断订单是否已申请售后）")
    @GetMapping("/by-order")
    public Result<AfterSaleVO> getByOrderId(@RequestParam Long orderId) {
        return Result.success(afterSaleService.getByOrderId(orderId));
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

    @Operation(summary = "买家确认上门取件（快递员已上门取走包裹，mock 动作）")
    @PostMapping("/{id}/pickup-complete")
    public Result<AfterSaleVO> pickupComplete(@PathVariable Long id) {
        return Result.success(afterSaleService.pickupComplete(id));
    }

    @Operation(summary = "售后变更流水（买家可见，不含商家内部备注）")
    @GetMapping("/{id}/logs")
    public Result<List<AfterSaleLogVO>> listLogs(@PathVariable Long id) {
        return Result.success(afterSaleService.listLogs(id));
    }
}
