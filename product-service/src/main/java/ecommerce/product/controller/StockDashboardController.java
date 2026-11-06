package ecommerce.product.controller;

import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.product.mapper.StockSkuMapper;
import ecommerce.product.mapper.StockTransactionMapper;
import ecommerce.product.vo.StockSkuVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "库存看板", description = "库存概览 + 低库存预警 + 出入库趋势")
@RestController
@RequirePerm("stock:dashboard:view")
@RequestMapping("/api/admin/stock/dashboard")
public class StockDashboardController {
    private final StockSkuMapper skuMapper;
    private final StockTransactionMapper txMapper;

    public StockDashboardController(StockSkuMapper skuMapper, StockTransactionMapper txMapper) {
        this.skuMapper = skuMapper;
        this.txMapper = txMapper;
    }

    @Operation(summary = "看板聚合：SKU总数 / 今日入库 / 今日出库 / 低库存SKU数")
    @GetMapping
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> data = new HashMap<>();
        data.put("skuTotal", skuMapper.totalCount());
        data.put("todayIn", txMapper.todayTotalIn());
        data.put("todayOut", txMapper.todayTotalOut());
        // 低库存 SKU 数 = min_stock > 0 AND stock < min_stock AND status = 1
        data.put("lowStockSku", skuMapper.countLowStock());
        return Result.success(data);
    }

    @Operation(summary = "低库存 TOP N SKU")
    @GetMapping("/low-stock")
    public Result<List<StockSkuVO>> lowStock(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(skuMapper.lowStockTopN(limit));
    }

    @Operation(summary = "近 N 天出入库按日聚合")
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "30") int days) {
        List<Object[]> rows = txMapper.dailyTrend(days);

        // 补全日期（没有出入库的日子也要返回，前端图表才不缺天）
        Map<String, long[]> map = new HashMap<>();
        for (Object[] row : rows) {
            String day = row[0].toString();
            long inQty = ((Number) row[1]).longValue();
            long outQty = ((Number) row[2]).longValue();
            map.put(day, new long[]{inQty, outQty});
        }

        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(days - 1);
        for (int i = 0; i < days; i++) {
            LocalDate d = start.plusDays(i);
            String dayStr = d.format(DateTimeFormatter.ISO_DATE);
            long[] vals = map.getOrDefault(dayStr, new long[]{0, 0});
            Map<String, Object> point = new HashMap<>();
            point.put("date", dayStr);
            point.put("in", vals[0]);
            point.put("out", vals[1]);
            result.add(point);
        }
        return Result.success(result);
    }
}
