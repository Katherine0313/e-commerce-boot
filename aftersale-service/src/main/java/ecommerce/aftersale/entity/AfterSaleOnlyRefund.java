package ecommerce.aftersale.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 仅退款子表实体（对应 after_sale_only_refunds 表）。
 * <p>
 * 一个售后单最多一条，只存操作时间戳（无退货字段），由 after_sale_type=only_refund 路由到本表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSaleOnlyRefund {
    private Long id;
    private Long afterSaleId;
    private LocalDateTime reviewAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime refundAt;
    private LocalDateTime refundedAt;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
