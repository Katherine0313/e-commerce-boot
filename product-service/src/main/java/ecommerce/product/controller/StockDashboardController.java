package ecommerce.product.controller;

import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.product.mapper.StockSkuMapper;
import ecommerce.product.mapper.StockTransactionMapper;
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

@Tag(name = "库存看板", description = "库存概览 + 低库存预警 + 出入库趋势 + 类型占比 + SKU 分布")
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

    @Operation(summary = "看板聚合：总库存件数 / SKU总数 / 今日入库 / 今日出库 / 低库存SKU数")
    @GetMapping
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> data = new HashMap<>();
        data.put("totalStock", txMapper.totalStock());
        data.put("skuTotal", skuMapper.totalCount());
        data.put("todayIn", txMapper.todayTotalIn());
        data.put("todayOut", txMapper.todayTotalOut());
        data.put("lowStockSku", skuMapper.countLowStock());
        return Result.success(data);
    }

    @Operation(summary = "近 N 天出入库按日聚合")
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "30") int days) {
        List<Map<String, Object>> rows = txMapper.dailyTrend(days);

        Map<String, long[]> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String day = String.valueOf(row.get("day"));
            long inQty = ((Number) row.get("in_qty")).longValue();
            long outQty = ((Number) row.get("out_qty")).longValue();
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

    @Operation(summary = "业务类型占比（近 N 天 IN/OUT/ADJUST）")
    @GetMapping("/type-dist")
    public Result<List<Map<String, Object>>> typeDist(@RequestParam(defaultValue = "30") int days) {
        List<Map<String, Object>> raw = txMapper.typeDist(days);
        // 补全 3 种类型（没数据的也返回 0，前端饼图不缺块）
        String[] allTypes = {"IN", "OUT", "ADJUST"};
        String[] labels = {"入库", "出库", "盘点调整"};
        java.util.Map<String, Map<String, Object>> map = new HashMap<>();
        for (Map<String, Object> row : raw) {
            map.put((String) row.get("type"), row);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < allTypes.length; i++) {
            Map<String, Object> entry = map.getOrDefault(allTypes[i], new HashMap<>());
            Map<String, Object> out = new HashMap<>();
            out.put("type", allTypes[i]);
            out.put("label", labels[i]);
            out.put("count", entry.getOrDefault("count", 0));
            out.put("qty", entry.getOrDefault("qty", 0));
            result.add(out);
        }
        return Result.success(result);
    }

    @Operation(summary = "SKU 库存分布 TOP N（水平柱状图用）")
    @GetMapping("/sku-dist")
    public Result<List<Map<String, Object>>> skuDist(@RequestParam(defaultValue = "15") int limit) {
        return Result.success(skuMapper.stockDistTopN(limit));
    }

    @Operation(summary = "低库存 TOP N SKU（带风险级别 + 今日出库量）")
    @GetMapping("/low-stock")
    public Result<List<Map<String, Object>>> lowStock(@RequestParam(defaultValue = "20") int limit) {
        // 用原生 SQL 带 CASE 算风险级别 + LEFT JOIN 今日出库量
        List<Map<String, Object>> rows = skuMapper.lowStockDetail(limit);
        // 把 riskLevel 中文标签加上
        for (Map<String, Object> r : rows) {
            String level = (String) r.get("riskLevel");
            r.put("riskLabel", switch (level) {
                case "urgent" -> "紧急";
                case "high" -> "高";
                case "mid" -> "中";
                default -> level;
            });
        }
        return Result.success(rows);
    }
}
