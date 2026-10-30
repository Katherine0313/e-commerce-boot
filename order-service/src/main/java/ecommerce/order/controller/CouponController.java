package ecommerce.order.controller;

import ecommerce.common.Result;
import ecommerce.order.dto.QuoteOrderDTO;
import ecommerce.order.service.CouponService;
import ecommerce.order.vo.AvailableCouponVO;
import ecommerce.order.vo.CouponCenterVO;
import ecommerce.order.vo.UserCouponVO;
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
 * C 端优惠券接口（需登录）。
 */
@Tag(name = "优惠券", description = "领券中心 / 领券 / 我的券 / 订单可用券")
@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @Operation(summary = "领券中心")
    @GetMapping("/center")
    public Result<List<CouponCenterVO>> center() {
        return Result.success(couponService.center());
    }

    @Operation(summary = "领取优惠券")
    @PostMapping("/{templateId}/claim")
    public Result<UserCouponVO> claim(@PathVariable Long templateId) {
        return Result.success(couponService.claim(templateId));
    }

    @Operation(summary = "我的优惠券（status：AVAILABLE / USED / EXPIRED）")
    @GetMapping("/mine")
    public Result<List<UserCouponVO>> mine(@RequestParam(required = false) String status) {
        return Result.success(couponService.myCoupons(status));
    }

    @Operation(summary = "当前订单可用券（后端算好每张券可减金额与不可用原因）")
    @PostMapping("/available")
    public Result<List<AvailableCouponVO>> available(@RequestBody QuoteOrderDTO dto) {
        return Result.success(couponService.available(dto.getItems()));
    }
}
