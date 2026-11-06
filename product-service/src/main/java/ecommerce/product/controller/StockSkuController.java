package ecommerce.product.controller;

import ecommerce.common.PageVO;
import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.product.dto.StockSkuAlertDTO;
import ecommerce.product.entity.ProductSku;
import ecommerce.product.mapper.StockSkuMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SKU 库存列表 + 预警阈值设置（/api/admin/stock/skus）。
 * 旧的 product 级预警 /api/admin/stock 已废弃（AdminStockController 不再返回有意义的 SKU 粒度数据）。
 */
@Tag(name = "SKU 库存", description = "SKU 级库存列表与预警阈值")
@RestController
@RequirePerm("product:sku-stock:list")
@RequestMapping("/api/admin/stock/skus")
public class StockSkuController {
    private final StockSkuMapper skuMapper;

    public StockSkuController(StockSkuMapper skuMapper) {
        this.skuMapper = skuMapper;
    }

    @Operation(summary = "SKU 库存列表（分页 + 搜索 + 低库存过滤）")
    @GetMapping
    public Result<PageVO<ProductSku>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean lowStockOnly) {
        int offset = (page - 1) * size;
        int total = skuMapper.pageCount(search, lowStockOnly);
        List<ProductSku> list = skuMapper.pageList(search, lowStockOnly, offset, size);
        PageVO<ProductSku> vo = new PageVO<>(list, total, page, size);
        return Result.success(vo);
    }

    @Operation(summary = "设置 SKU 预警阈值")
    @RequirePerm("product:sku-stock:list")
    @PostMapping("/{skuId}/alert")
    public Result<Void> setAlert(@PathVariable Long skuId, @RequestBody StockSkuAlertDTO dto) {
        skuMapper.updateAlert(skuId, dto.getMinStock(), dto.getMaxStock());
        return Result.success();
    }
}
