package ecommerce.aftersale.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 退货退款子表实体（对应 after_sale_returns 表）。
 * <p>
 * 一个售后单最多一条，存储退货方式 + 各操作时间戳，由 after_sale_type=return_refund 路由到本表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSaleReturn {
    private Long id;
    private Long shopId;
    private Long afterSaleId;
    private String returnWay;
    private String returnTimeSlot;
    private String returnCourier;
    private String returnTrackingNumber;
    private LocalDateTime returnAt;
    private LocalDateTime reviewAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime refundAt;
    private LocalDateTime refundedAt;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
