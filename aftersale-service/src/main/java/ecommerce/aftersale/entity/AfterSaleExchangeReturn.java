package ecommerce.aftersale.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 换货子表实体（对应 after_sale_exchange_returns 表）。
 * <p>
 * 一个售后单最多一条，存储退货方式 + 换货各操作时间戳，由 after_sale_type=exchange 路由到本表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSaleExchangeReturn {
    private Long id;
    private Long shopId;
    private Long afterSaleId;
    private String returnWay;
    private String returnTimeSlot;
    private LocalDateTime reviewAt;
    private LocalDateTime returnAt;
    private LocalDateTime exchangeAt;
    private LocalDateTime exchangedAt;
    private LocalDateTime rejectedAt;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
