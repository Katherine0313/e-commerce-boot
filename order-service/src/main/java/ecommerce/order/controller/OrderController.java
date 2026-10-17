package ecommerce.order.controller;

import ecommerce.common.Result;
import ecommerce.order.dto.CreateOrderDTO;
import ecommerce.order.service.OrderService;
import ecommerce.order.vo.LogisticsVO;
import ecommerce.order.vo.OrderVO;
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
import java.util.Map;

/**
 * 订单接口（需登录）。
 * <p>
 * 状态流转照原 Node 后端：下单(待付款) → 支付(待确认) → 确认收款(待发货) → 发货(已发货) → 完成(已完成)，
 * 待付款/待确认/待发货可取消。confirm/ship 本质是商家操作，后续拆 /api/admin/orders 时再收紧权限。
 */
@Tag(name = "订单", description = "订单接口（需登录）")
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "下单")
    @PostMapping
    public Result<OrderVO> createOrder(@RequestBody CreateOrderDTO dto) {
        return Result.success(orderService.createOrder(dto));
    }

    @Operation(summary = "订单列表")
    @GetMapping
    public Result<List<OrderVO>> listOrders(@RequestParam(required = false) String keyword) {
        return Result.success(orderService.listOrders(keyword));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{id}")
    public Result<OrderVO> getOrder(@PathVariable Long id) {
        return Result.success(orderService.getOrder(id));
    }

    @Operation(summary = "物流轨迹（假数据）")
    @GetMapping("/{id}/logistics")
    public Result<LogisticsVO> getLogistics(@PathVariable Long id) {
        return Result.success(orderService.getLogistics(id));
    }

    @Operation(summary = "支付（待付款 → 待确认）")
    @PutMapping("/{id}/pay")
    public Result<OrderVO> payOrder(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String payType = body == null ? null : (String) body.get("payType");
        return Result.success(orderService.payOrder(id, payType));
    }

    @Operation(summary = "确认收款（待确认 → 待发货）")
    @PutMapping("/{id}/confirm")
    public Result<OrderVO> confirmOrder(@PathVariable Long id) {
        return Result.success(orderService.confirmOrder(id));
    }

    @Operation(summary = "发货（待发货 → 已发货）")
    @PutMapping("/{id}/ship")
    public Result<OrderVO> shipOrder(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String courier = body == null ? null : (String) body.get("courier");
        String trackingNumber = body == null ? null : (String) body.get("trackingNumber");
        return Result.success(orderService.shipOrder(id, courier, trackingNumber));
    }

    @Operation(summary = "完成订单（已发货 → 已完成）")
    @PutMapping("/{id}/finish")
    public Result<OrderVO> finishOrder(@PathVariable Long id) {
        return Result.success(orderService.finishOrder(id));
    }

    @Operation(summary = "取消订单（恢复库存 + 改取消）")
    @PutMapping("/{id}/cancel")
    public Result<OrderVO> cancelOrder(@PathVariable Long id) {
        return Result.success(orderService.cancelOrder(id));
    }

    @Operation(summary = "删除订单（软删：is_deleted=1）")
    @DeleteMapping("/{id}")
    public Result<Void> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return Result.success(null);
    }
}
