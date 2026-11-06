package ecommerce.order.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收款单列表 / 详情 VO。
 * <p>
 * 单号、渠道等字段直接来自 finance_receipt；买家姓名、账户名称、订单状态、退款标记由 SQL 关联带出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceReceiptVO {
    private Long id;
    private String receiptNo;
    private Long orderId;
    private String orderNo;
    private Long userId;
    /** 买家昵称（user.name） */
    private String userName;
    private String channel;
    private String channelDesc;
    private Long accountId;
    /** 收款账户名称 */
    private String accountName;
    private BigDecimal amount;
    private LocalDateTime paidAt;
    private Integer status;
    private String statusDesc;
    private Long auditBy;
    private String auditName;
    private LocalDateTime auditTime;
    private String remark;
    private LocalDateTime createTime;
    /** 订单状态（orders.order_status） */
    private Integer orderStatus;
    /** 收货人（详情里做订单摘要） */
    private String consignee;
    /** 该订单是否已退款（售后 status=refunded） */
    private Boolean refunded;
    /** 已退款金额 */
    private BigDecimal refundAmount;
    /** 退款完成时间（取自 after_sale_only_refunds / after_sale_returns 的 refunded_at） */
    private LocalDateTime refundedAt;
}
