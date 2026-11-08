package ecommerce.aftersale.controller;

import ecommerce.aftersale.entity.AfterSale;
import ecommerce.aftersale.enums.AfterSaleReasonEnum;
import ecommerce.aftersale.enums.AfterSaleStatusEnum;
import ecommerce.aftersale.enums.AfterSaleTypeEnum;
import ecommerce.aftersale.mapper.AfterSaleLogMapper;
import ecommerce.aftersale.mapper.AfterSaleMapper;
import ecommerce.aftersale.support.AfterSaleLogConverter;
import ecommerce.aftersale.vo.AfterSaleLogVO;
import ecommerce.common.Result;
import ecommerce.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 内部接口（/internal/ai/**）：Python AI 服务（FastAPI）内网调用，查售后进度。
 * <p>
 * 鉴权靠 common 的 InternalApiInterceptor（X-Internal-Token 共享密钥），不加权限注解。
 * userId 可选：传入时校验归属，售后单不属于该用户按「不存在」处理（不泄露他人工单存在性）。
 */
@Tag(name = "AI 内部接口", description = "Python AI 服务内部调用（X-Internal-Token 鉴权）")
@RestController
@RequestMapping("/internal/ai")
public class InternalAiController {

    private final AfterSaleMapper afterSaleMapper;
    private final AfterSaleLogMapper afterSaleLogMapper;

    public InternalAiController(AfterSaleMapper afterSaleMapper, AfterSaleLogMapper afterSaleLogMapper) {
        this.afterSaleMapper = afterSaleMapper;
        this.afterSaleLogMapper = afterSaleLogMapper;
    }

    @Operation(summary = "售后单详情（含状态流转记录；userId 传入时校验归属）")
    @GetMapping("/aftersale/{id}")
    public Result<AfterSaleDetailVO> getAfterSale(@PathVariable Long id,
                                                  @RequestParam(required = false) Long userId) {
        // findById 已 JOIN orders 带出 user_id，归属校验无需再查订单
        AfterSale afterSale = afterSaleMapper.findById(id);
        if (afterSale == null || (userId != null && !userId.equals(afterSale.getUserId()))) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "售后单不存在");
        }
        AfterSaleTypeEnum type = AfterSaleTypeEnum.fromCode(afterSale.getAfterSaleType());
        AfterSaleStatusEnum status = AfterSaleStatusEnum.fromCode(afterSale.getStatus());
        AfterSaleReasonEnum reason = AfterSaleReasonEnum.fromCode(afterSale.getReason());
        // 流转记录按买家可见口径转换：不含商家内部备注
        List<AfterSaleLogVO> logs = AfterSaleLogConverter.toVOList(
                afterSaleLogMapper.listByAfterSaleId(id), false);
        return Result.success(AfterSaleDetailVO.builder()
                .id(afterSale.getId())
                .aftersaleNo(afterSale.getAfterSaleNo())
                .orderNo(afterSale.getOrderNo())
                .type(afterSale.getAfterSaleType())
                .typeDesc(type == null ? null : type.getDesc())
                .status(afterSale.getStatus())
                .statusDesc(status == null ? null : status.getDesc())
                .reason(afterSale.getReason())
                .reasonDesc(reason == null ? null : reason.getDesc())
                .amount(afterSale.getRefundAmount())
                .createdAt(afterSale.getCreatedAt())
                .logs(logs)
                .build());
    }

    /** AI 售后详情（时间字段走全局 Jackson 格式 yyyy-MM-dd HH:mm:ss） */
    @Data
    @Builder
    public static class AfterSaleDetailVO {
        private Long id;
        private String aftersaleNo;
        private String orderNo;
        /** 售后类型 code（only_refund / return_refund / exchange） */
        private String type;
        private String typeDesc;
        /** 售后状态 code（reviewing/returning/refunding/exchanging/exchanged/refunded/rejected） */
        private String status;
        private String statusDesc;
        /** 售后原因 code */
        private String reason;
        private String reasonDesc;
        /** 退款金额（申请时快照） */
        private BigDecimal amount;
        private LocalDateTime createdAt;
        /** 状态流转记录（按时间正序） */
        private List<AfterSaleLogVO> logs;
    }
}
