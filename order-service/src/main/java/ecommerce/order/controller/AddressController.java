package ecommerce.order.controller;

import ecommerce.common.Result;
import ecommerce.order.dto.AddAddressDTO;
import ecommerce.order.dto.UpdateAddressDTO;
import ecommerce.order.service.AddressService;
import ecommerce.order.vo.AddressVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收货地址接口（需登录）。
 * <p>
 * /api/address/** 不在 common 的 EXCLUDE_AUTH_PATHS 白名单内，JwtInterceptor 自动要求 user 令牌；
 * 当前用户从 UserContext.getUserId() 取。
 */
@Tag(name = "收货地址", description = "收货地址接口（需登录）")
@RestController
@RequestMapping("/api/address")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @Operation(summary = "地址列表")
    @GetMapping
    public Result<List<AddressVO>> list() {
        return Result.success(addressService.list());
    }

    @Operation(summary = "新增地址")
    @PostMapping
    public Result<Void> add(@RequestBody AddAddressDTO dto) {
        addressService.add(dto);
        return Result.success();
    }

    @Operation(summary = "修改地址")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody UpdateAddressDTO dto) {
        addressService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除地址")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        addressService.remove(id);
        return Result.success();
    }
}
