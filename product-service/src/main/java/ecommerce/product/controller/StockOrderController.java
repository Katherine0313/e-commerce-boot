package ecommerce.product.controller;

import ecommerce.common.PageVO;
import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.exception.BusinessException;
import ecommerce.product.dto.StockOrderCreateDTO;
import ecommerce.product.entity.StockOrder;
import ecommerce.product.mapper.StockOrderItemMapper;
import ecommerce.product.mapper.StockOrderMapper;
import ecommerce.product.service.StockOrderService;
import ecommerce.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "库存单据", description = "入库/出库/调整单")
@RestController
@RequestMapping("/api/admin/stock/orders")
public class StockOrderController {
    private final StockOrderService orderService;
    private final StockOrderMapper orderMapper;
    private final StockOrderItemMapper itemMapper;
    private final JdbcTemplate jdbc;

    public StockOrderController(StockOrderService orderService,
                                StockOrderMapper orderMapper,
                                StockOrderItemMapper itemMapper,
                                JdbcTemplate jdbc) {
        this.orderService = orderService;
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.jdbc = jdbc;
    }

    private String currentOperator() {
        // UserContext 是 common 的，后台请求肯定带 admin 登录信息
        Long uid = UserContext.getUserId();
        String name = UserContext.getUsername();
        return (name != null ? name : "admin") + (uid != null ? "(" + uid + ")" : "");
    }

    /** 按单据类型分别校验权限（一个 Controller 承载三种单据） */
    private void checkCreatePerm(String type) {
        List<String> perms = UserContext.getPermissions();
        if (perms.contains("*:*:*")) return;
        String required = switch (type) {
            case "RECEIPT" -> "stock:receipt:manage";
            case "ADJUST" -> "stock:adjust:manage";
            case "SHIPMENT" -> "stock:shipment:view";
            default -> throw new BusinessException("未知单据类型: " + type);
        };
        if (!perms.contains(required)) {
            throw new BusinessException(org.springframework.http.HttpStatus.FORBIDDEN, "无权创建该类型单据");
        }
    }

    @Operation(summary = "单据列表（分页 + 类型/状态/时间过滤）")
    @GetMapping
    @RequirePerm("stock:transaction:view")
    public Result<PageVO<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String relatedNo,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        int offset = (page - 1) * size;
        StringBuilder where = new StringBuilder("WHERE 1=1");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (type != null && !type.isBlank()) { where.append(" AND o.type = ?"); args.add(type); }
        if (bizType != null && !bizType.isBlank()) { where.append(" AND o.biz_type = ?"); args.add(bizType); }
        if (status != null && !status.isBlank()) { where.append(" AND o.status = ?"); args.add(Integer.parseInt(status)); }
        if (relatedNo != null && !relatedNo.isBlank()) { where.append(" AND o.related_no LIKE ?"); args.add("%" + relatedNo + "%"); }
        if (startTime != null && !startTime.isBlank()) { where.append(" AND o.create_time >= ?"); args.add(startTime); }
        if (endTime != null && !endTime.isBlank()) { where.append(" AND o.create_time < DATE_ADD(?, INTERVAL 1 DAY)"); args.add(endTime); }

        Integer total = jdbc.queryForObject(
            "SELECT COUNT(1) FROM stock_order o " + where,
            Integer.class, args.toArray());

        args.add(offset);
        args.add(size);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT o.* FROM stock_order o " + where + " ORDER BY o.id DESC LIMIT ?, ?",
            args.toArray()).stream()
            // JdbcTemplate 行 Map 的 key 是数据库列名（snake_case），前端按 camelCase 取值，统一转换
            .map(StockOrderController::toCamelRow)
            .toList();

        return Result.success(new PageVO<>(rows, total != null ? total : 0, page, size));
    }

    private static Map<String, Object> toCamelRow(Map<String, Object> row) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        row.forEach((k, v) -> out.put(snakeToCamel(k), v));
        return out;
    }

    private static String snakeToCamel(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean upper = false;
        for (char c : s.toCharArray()) {
            if (c == '_') { upper = true; }
            else if (upper) { sb.append(Character.toUpperCase(c)); upper = false; }
            else { sb.append(c); }
        }
        return sb.toString();
    }

    @Operation(summary = "单据详情（主表 + 明细）")
    @GetMapping("/{id}")
    @RequirePerm("stock:transaction:view")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        StockOrder order = orderMapper.findById(id);
        if (order == null) throw new BusinessException("单据不存在");
        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("items", itemMapper.findByOrderId(id));
        return Result.success(result);
    }

    @Operation(summary = "创建单据（创建即已完成，一个事务里逐行写 stock_transaction + 更新 product_sku.stock）")
    @PostMapping
    public Result<StockOrder> create(@RequestBody StockOrderCreateDTO dto) {
        checkCreatePerm(dto.getType());
        StockOrder order = orderService.createAndComplete(dto, UserContext.getUserId(), currentOperator());
        return Result.success(order);
    }

    @Operation(summary = "取消单据")
    @PostMapping("/{id}/cancel")
    @RequirePerm("stock:receipt:manage")
    public Result<Void> cancel(@PathVariable Long id) {
        orderService.cancel(id);
        return Result.success();
    }
}
