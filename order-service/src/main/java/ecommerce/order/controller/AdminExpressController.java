package ecommerce.order.controller;

import ecommerce.common.PageVO;
import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.order.dto.DeliveryExpressDTO;
import ecommerce.order.service.DeliveryExpressService;
import ecommerce.order.vo.DeliveryExpressVO;
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
 * 后台快递公司字典接口（/api/admin/expresses）。
 * <p>
 * 快递公司是数据字典，启用的才能在发货弹窗里选。
 */
@Tag(name = "后台快递公司", description = "快递公司字典维护")
@RestController
@RequestMapping("/api/admin/expresses")
public class AdminExpressController {
    private final DeliveryExpressService deliveryExpressService;

    public AdminExpressController(DeliveryExpressService deliveryExpressService) {
        this.deliveryExpressService = deliveryExpressService;
    }

    @Operation(summary = "快递公司列表（分页 + 名称/编码模糊 + 状态筛选）")
    @RequirePerm("order:express:list")
    @GetMapping
    public Result<PageVO<DeliveryExpressVO>> list(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int pageSize,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Integer status) {
        return Result.success(deliveryExpressService.listAll(page, pageSize, keyword, status));
    }

    @Operation(summary = "启用中的快递公司（下拉数据源）")
    @RequirePerm("order:express:list")
    @GetMapping("/enabled")
    public Result<List<DeliveryExpressVO>> enabled() {
        return Result.success(deliveryExpressService.listEnabled());
    }

    @Operation(summary = "新建快递公司")
    @RequirePerm("order:express:save")
    @PostMapping
    public Result<DeliveryExpressVO> create(@RequestBody DeliveryExpressDTO dto) {
        return Result.success(deliveryExpressService.create(dto));
    }

    @Operation(summary = "编辑快递公司")
    @RequirePerm("order:express:save")
    @PutMapping("/{id}")
    public Result<DeliveryExpressVO> update(@PathVariable Long id, @RequestBody DeliveryExpressDTO dto) {
        return Result.success(deliveryExpressService.update(id, dto));
    }

    @Operation(summary = "删除快递公司（软删）")
    @RequirePerm("order:express:remove")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        deliveryExpressService.delete(id);
        return Result.success();
    }
}
