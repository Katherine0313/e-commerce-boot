package ecommerce.order.controller;

import ecommerce.common.PageVO;
import ecommerce.common.Result;
import ecommerce.order.dto.AdminCouponDTO;
import ecommerce.order.dto.AdminCouponGrantDTO;
import ecommerce.order.service.AdminCouponService;
import ecommerce.order.vo.AdminCouponVO;
import ecommerce.order.vo.CouponGrantResultVO;
import ecommerce.order.vo.CouponRecordVO;
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
 * 后台优惠券管理接口。
 */
@Tag(name = "后台优惠券管理", description = "券模板创建、发放、停止与领取统计")
@RestController
@RequestMapping("/api/admin/coupons")
public class AdminCouponController {

    private final AdminCouponService adminCouponService;

    public AdminCouponController(AdminCouponService adminCouponService) {
        this.adminCouponService = adminCouponService;
    }

    @Operation(summary = "券列表（分页 + 名称/状态筛选）")
    @GetMapping
    public Result<PageVO<AdminCouponVO>> list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int pageSize,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String status) {
        return Result.success(adminCouponService.list(page, pageSize, keyword, status));
    }

    @Operation(summary = "券详情")
    @GetMapping("/{id}")
    public Result<AdminCouponVO> get(@PathVariable Long id) {
        return Result.success(adminCouponService.get(id));
    }

    @Operation(summary = "新建优惠券")
    @PostMapping
    public Result<Long> create(@RequestBody AdminCouponDTO dto) {
        return Result.success(adminCouponService.create(dto));
    }

    @Operation(summary = "编辑优惠券（仅未开始发放）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AdminCouponDTO dto) {
        adminCouponService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "停止发放")
    @PostMapping("/{id}/stop")
    public Result<Void> stop(@PathVariable Long id) {
        adminCouponService.stop(id);
        return Result.success();
    }

    @Operation(summary = "删除优惠券（仅未开始且无人领取）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminCouponService.delete(id);
        return Result.success();
    }

    @Operation(summary = "定向/全量发券")
    @PostMapping("/{id}/grant")
    public Result<CouponGrantResultVO> grant(@PathVariable Long id, @RequestBody AdminCouponGrantDTO dto) {
        return Result.success(adminCouponService.grant(id, dto));
    }

    @Operation(summary = "领取/使用记录")
    @GetMapping("/{id}/records")
    public Result<PageVO<CouponRecordVO>> records(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(adminCouponService.records(id, page, pageSize));
    }
}
