package ecommerce.product.controller;

import ecommerce.common.Result;
import ecommerce.product.service.BannerService;
import ecommerce.product.vo.BannerVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Banner 浏览接口（小程序 / PC 商城轮播，免登录）。
 * <p>
 * 这些接口在 common 的 WebMvcConfig.EXCLUDE_AUTH_PATHS 白名单内，不带 token 也能访问。
 */
@Tag(name = "Banner 浏览", description = "轮播广告查询与点击统计（免登录）")
@RestController
@RequestMapping("/api/banners")
public class BannerController {

    private final BannerService bannerService;

    public BannerController(BannerService bannerService) {
        this.bannerService = bannerService;
    }

    @Operation(summary = "某个位置上启用的 Banner（position：1 小程序首页 2 小程序分类页 11 PC商城首页）")
    @GetMapping
    public Result<List<BannerVO>> list(@RequestParam(required = false) Integer position) {
        return Result.success(bannerService.listEnabled(position));
    }

    @Operation(summary = "Banner 点击统计（次数 +1）")
    @PostMapping("/{id}/browse")
    public Result<Void> browse(@PathVariable Long id) {
        bannerService.browse(id);
        return Result.success();
    }
}
