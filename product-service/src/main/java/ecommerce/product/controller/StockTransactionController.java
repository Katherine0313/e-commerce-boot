package ecommerce.product.controller;

import ecommerce.common.PageVO;
import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.product.entity.StockTransaction;
import ecommerce.product.mapper.StockTransactionMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "库存流水", description = "出入库审计流水查询")
@RestController
@RequirePerm("stock:transaction:view")
@RequestMapping("/api/admin/stock/transactions")
public class StockTransactionController {
    private final StockTransactionMapper transactionMapper;

    public StockTransactionController(StockTransactionMapper transactionMapper) {
        this.transactionMapper = transactionMapper;
    }

    @Operation(summary = "流水分页查询")
    @GetMapping
    public Result<PageVO<StockTransaction>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) Long orderItemId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        int offset = (page - 1) * size;
        int total = transactionMapper.pageCount(skuId, type, reason, orderItemId, startTime, endTime);
        List<StockTransaction> list = transactionMapper.pageList(skuId, type, reason, orderItemId, startTime, endTime, offset, size);
        return Result.success(new PageVO<>(list, total, page, size));
    }
}
