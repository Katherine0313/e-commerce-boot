package ecommerce.order.controller;

import ecommerce.common.PageVO;
import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.order.dto.DeliveryCreateDTO;
import ecommerce.order.dto.DeliveryQueryDTO;
import ecommerce.order.service.OrderDeliveryService;
import ecommerce.order.vo.OrderDeliveryLogVO;
import ecommerce.order.vo.OrderDeliveryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * 后台发货管理接口（/api/admin/deliveries）。
 * <p>
 * 发货（待发货→已发货）、补发（订单状态不动）、改快递信息、包裹列表与发货流水。
 */
@Tag(name = "后台发货管理", description = "包裹列表 + 发货/补发 + 发货流水")
@RestController
@RequirePerm("order:delivery:list")
@RequestMapping("/api/admin/deliveries")
public class AdminDeliveryController {
    private final OrderDeliveryService orderDeliveryService;

    public AdminDeliveryController(OrderDeliveryService orderDeliveryService) {
        this.orderDeliveryService = orderDeliveryService;
    }

    @Operation(summary = "包裹列表（分页 + 订单号/单号/快递公司/发货类型筛选）")
    @GetMapping
    public Result<PageVO<OrderDeliveryVO>> list(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int pageSize,
                                                @RequestParam(required = false) String orderNo,
                                                @RequestParam(required = false) String trackingNumber,
                                                @RequestParam(required = false) String expressCode,
                                                @RequestParam(required = false) Integer deliveryType) {
        DeliveryQueryDTO query = new DeliveryQueryDTO();
        query.setOrderNo(orderNo);
        query.setTrackingNumber(trackingNumber);
        query.setExpressCode(expressCode);
        query.setDeliveryType(deliveryType);
        return Result.success(orderDeliveryService.listAdmin(page, pageSize, query));
    }

    @Operation(summary = "发货（待发货 → 已发货）")
    @RequirePerm("order:delivery:ship")
    @PostMapping
    public Result<OrderDeliveryVO> ship(@RequestParam Long orderId, @RequestBody DeliveryCreateDTO dto) {
        return Result.success(orderDeliveryService.ship(orderId, dto));
    }

    @Operation(summary = "补发（订单状态不动，追加一个包裹）")
    @RequirePerm("order:delivery:resend")
    @PostMapping("/resend")
    public Result<OrderDeliveryVO> resend(@RequestParam Long orderId, @RequestBody DeliveryCreateDTO dto) {
        return Result.success(orderDeliveryService.resend(orderId, dto));
    }

    @Operation(summary = "修改包裹的快递信息（改单号/备注）")
    @RequirePerm("order:delivery:ship")
    @PutMapping("/{id}")
    public Result<OrderDeliveryVO> update(@PathVariable Long id, @RequestBody DeliveryCreateDTO dto) {
        return Result.success(orderDeliveryService.update(id, dto));
    }

    @Operation(summary = "某订单的发货流水")
    @GetMapping("/logs")
    public Result<List<OrderDeliveryLogVO>> logs(@RequestParam Long orderId) {
        return Result.success(orderDeliveryService.listLogs(orderId));
    }
}
