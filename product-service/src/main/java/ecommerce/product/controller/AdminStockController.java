package ecommerce.product.controller;

import ecommerce.common.Result;
import ecommerce.product.dto.AdminStockDTO;
import ecommerce.product.service.AdminStockService;
import ecommerce.product.vo.StockStatusVO;
import ecommerce.product.vo.StockVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台库存预警管理接口（/api/admin/stock）。
 * <p>
 * 路径 /api/admin/** 由 JwtInterceptor 按 admin 身份分流校验，需 admin 令牌。
 */
@Tag(name = "后台库存预警", description = "库存预警配置（管理员）")
@RestController
@RequestMapping("/api/admin/stock")
public class AdminStockController {
    private final AdminStockService adminStockService;

    public AdminStockController(AdminStockService adminStockService) {
        this.adminStockService = adminStockService;
    }

    @Operation(summary = "库存预警列表（含未配置预警的商品，支持商品名搜索）")
    @GetMapping
    public Result<List<StockVO>> list(@RequestParam(required = false) String search) {
        return Result.success(adminStockService.list(search));
    }

    @Operation(summary = "库存状态常量（中文 + 颜色）")
    @GetMapping("/status-constants")
    public Result<StockStatusVO> statusConstants() {
        return Result.success(adminStockService.statusConstants());
    }

    @Operation(summary = "创建/更新库存预警（product_id 重复自动更新）")
    @PostMapping
    public Result<Void> create(@RequestBody AdminStockDTO dto) {
        adminStockService.create(dto);
        return Result.success();
    }
}
