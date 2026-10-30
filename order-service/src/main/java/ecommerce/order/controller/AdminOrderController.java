package ecommerce.order.controller;

import ecommerce.common.PageVO;
import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.order.dto.AdminOrderDTO;
import ecommerce.order.service.AdminOrderService;
import ecommerce.order.vo.LogisticsVO;
import ecommerce.order.vo.OrderVO;
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
import java.util.Map;

/**
 * 后台订单管理接口（/api/admin/orders）。
 * <p>
 * 商家操作：查所有订单、确认收款/发货/完成/取消/删除/批量删除，走 admin 身份。
 */
@Tag(name = "后台订单管理", description = "订单列表 + 商家履约操作（管理员）")
@RestController
@RequirePerm("order:order:list")
@RequestMapping("/api/admin/orders")
public class AdminOrderController {
    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    @Operation(summary = "订单列表（分页 + 状态/关键词/时间范围筛选）")
    @GetMapping
    public Result<PageVO<OrderVO>> list(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "13") int pageSize,
                                        @RequestParam(required = false) Integer status,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String startDate,
                                        @RequestParam(required = false) String endDate,
                                        @RequestParam(required = false) String dateField) {
        return Result.success(adminOrderService.listAll(page, pageSize, status, keyword, startDate, endDate, dateField));
    }

    @Operation(summary = "订单详情（含明细与当前库存）")
    @GetMapping("/{id}")
    public Result<OrderVO> get(@PathVariable Long id) {
        return Result.success(adminOrderService.getOrder(id));
    }

    @Operation(summary = "订单状态常量（map=code→后台文案，color=code→颜色）")
    @GetMapping("/status-constants")
    public Result<Map<String, Object>> statusConstants() {
        return Result.success(adminOrderService.getStatusConstants());
    }

    @Operation(summary = "快递公司列表")
    @GetMapping("/couriers")
    public Result<List<Map<String, String>>> couriers() {
        return Result.success(adminOrderService.getCouriers());
    }

    @Operation(summary = "更新订单信息（收货人/电话/地址/备注）")
    @RequirePerm("order:order:update")
    @PutMapping("/{id}")
    public Result<OrderVO> update(@PathVariable Long id, @RequestBody AdminOrderDTO dto) {
        return Result.success(adminOrderService.updateOrder(id, dto));
    }

    @Operation(summary = "确认收款（待确认 → 待发货）")
    @RequirePerm("order:order:update")
    @PutMapping("/{id}/confirm")
    public Result<OrderVO> confirm(@PathVariable Long id) {
        return Result.success(adminOrderService.confirmOrder(id));
    }

    @Operation(summary = "发货（待发货 → 已发货）")
    @RequirePerm("order:order:ship")
    @PutMapping("/{id}/ship")
    public Result<OrderVO> ship(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String courier = body == null ? null : (String) body.get("courier");
        String trackingNumber = body == null ? null : (String) body.get("trackingNumber");
        return Result.success(adminOrderService.shipOrder(id, courier, trackingNumber));
    }

    @Operation(summary = "完成（已发货 → 已完成）")
    @RequirePerm("order:order:update")
    @PutMapping("/{id}/finish")
    public Result<OrderVO> finish(@PathVariable Long id) {
        return Result.success(adminOrderService.finishOrder(id));
    }

    @Operation(summary = "取消（恢复库存 + 改取消）")
    @RequirePerm("order:order:update")
    @PutMapping("/{id}/cancel")
    public Result<OrderVO> cancel(@PathVariable Long id) {
        return Result.success(adminOrderService.cancelOrder(id));
    }

    @Operation(summary = "删除订单（软删）")
    @RequirePerm("order:order:remove")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminOrderService.deleteOrder(id);
        return Result.success();
    }

    @Operation(summary = "批量删除订单")
    @RequirePerm("order:order:remove")
    @DeleteMapping("/batch-delete")
    public Result<Void> batchDelete(@RequestBody Map<String, List<Long>> body) {
        adminOrderService.batchDelete(body.get("ids"));
        return Result.success();
    }

    @Operation(summary = "物流轨迹（假数据）")
    @GetMapping("/{id}/logistics")
    public Result<LogisticsVO> logistics(@PathVariable Long id) {
        return Result.success(adminOrderService.getLogistics(id));
    }

}
