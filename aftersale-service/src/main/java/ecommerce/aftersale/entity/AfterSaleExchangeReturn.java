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
    private Long afterSaleId;
    private String returnWay;
    private String returnTimeSlot;
    private String returnCourier;
    private String returnTrackingNumber;
    private String shipAddress;
    /** 买家确认上门取件时间 */
    private LocalDateTime pickupAt;
    /** 模拟取件单号 */
    private String pickupNo;
    /** 商家补发快递公司 */
    private String shipCourier;
    /** 商家补发快递单号 */
    private String shipTrackingNumber;
    /** 商家补发时间 */
    private LocalDateTime shipAt;
    private LocalDateTime reviewAt;
    private LocalDateTime returnAt;
    private LocalDateTime exchangeAt;
    private LocalDateTime exchangedAt;
    private LocalDateTime rejectedAt;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
