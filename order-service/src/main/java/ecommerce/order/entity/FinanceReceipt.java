package ecommerce.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收款单实体（对应 finance_receipt 表）。
 * <p>
 * 支付成功时与订单同一事务生成，order_id 唯一索引保证一笔订单只有一张单据；
 * 审核后 status=1 即为终态，不提供反审核。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceReceipt {
    private Long id;
    /** 收款单号：FIN + yyyyMMddHHmmssSSS + 4 位随机 */
    private String receiptNo;
    private Long orderId;
    private String orderNo;
    private Long userId;
    /** 支付渠道，来自 orders.pay_type */
    private String channel;
    /** 收款账户 ID；渠道未配置账户时为空，不阻塞支付 */
    private Long accountId;
    private BigDecimal amount;
    private LocalDateTime paidAt;
    /** 状态：0 待审核 / 1 已审核 */
    private Integer status;
    private Long auditBy;
    private String auditName;
    private LocalDateTime auditTime;
    private String remark;
    private Integer isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
