package ecommerce.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 发货操作流水实体（对应 order_delivery_log 表）。
 * <p>
 * 同步同事务写入（同 after_sale_log 的做法），后台「查看流水」读这张表。
 * 与 operation_log 的区别：operation_log 是通用审计、异步脱事务、且没有查询接口。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDeliveryLog {
    /** 操作人类型：系统 */
    public static final int OPERATOR_TYPE_SYSTEM = 1;
    /** 操作人类型：后台管理员 */
    public static final int OPERATOR_TYPE_ADMIN = 2;

    private Long id;
    private Long deliveryId;
    /** 包裹编号（快照） */
    private String packageNo;
    private Long orderId;
    /** 订单编号（快照） */
    private String orderNo;
    /** 操作类型：见 DeliveryOperateTypeEnum */
    private Integer operateType;
    /** 操作说明，如「发货成功：顺丰速运 SF123456」 */
    private String content;
    /** 内部备注（仅后台可见） */
    private String remark;
    /** 操作人 ID（后台管理员） */
    private Long operatorId;
    /** 操作人类型：1 系统 2 后台管理员 */
    private Integer operatorType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 软删除：1 已删除 0 正常 */
    private Integer isDeleted;
}
