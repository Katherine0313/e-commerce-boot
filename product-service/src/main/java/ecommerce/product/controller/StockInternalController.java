package ecommerce.product.controller;

import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.exception.BusinessException;
import ecommerce.product.dto.StockWriteDTO;
import ecommerce.product.service.StockWriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部接口：order-service / aftersale-service 调 product-service 的唯一出入库入口。
 * <p>
 * 不走网关鉴权（common.WebMvcConfig 已把 /internal/** 加入免登录白名单），
 * 但校验 X-Internal-Call 请求头，防止外部直接调。
 */
@Tag(name = "库存内部接口", description = "服务间出入库调用")
@RestController
@RequestMapping("/internal")
public class StockInternalController {
    private final StockWriteService stockWriteService;

    public StockInternalController(StockWriteService stockWriteService) {
        this.stockWriteService = stockWriteService;
    }

    @Operation(summary = "唯一出入库入口")
    @PostMapping("/stock-write")
    public Result<Void> write(@RequestBody StockWriteDTO dto,
                              @RequestHeader(value = "X-Internal-Call", required = false) String internal) {
        if (!"true".equals(internal)) {
            throw new BusinessException("非法内部调用，缺少 X-Internal-Call: true 头");
        }
        stockWriteService.write(dto);
        return Result.success();
    }
}
