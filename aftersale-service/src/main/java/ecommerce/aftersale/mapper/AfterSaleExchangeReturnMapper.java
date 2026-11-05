package ecommerce.aftersale.mapper;

import ecommerce.aftersale.entity.AfterSaleExchangeReturn;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 换货子表 Mapper（after_sale_exchange_returns 表）。
 */
public interface AfterSaleExchangeReturnMapper {

    // 插入占位行（创建售后时写，时间戳/退货字段先为 NULL）
    int insertPlaceholder(AfterSaleExchangeReturn exchangeReturn);

    // 按售后单查子表（不含软删除）
    AfterSaleExchangeReturn findByAfterSaleId(@Param("afterSaleId") Long afterSaleId);

    // 用户提交退货方式：写 return_way/return_time_slot/return_at
    int updateReturnInfo(@Param("afterSaleId") Long afterSaleId,
                         @Param("returnWay") String returnWay, @Param("returnTimeSlot") String returnTimeSlot,
                         @Param("returnAt") LocalDateTime returnAt, @Param("updatedAt") LocalDateTime updatedAt);

    // 回填退货物流：写 return_courier/return_tracking_number/return_at
    int updateTracking(@Param("afterSaleId") Long afterSaleId,
                       @Param("courier") String courier, @Param("trackingNumber") String trackingNumber,
                       @Param("returnAt") LocalDateTime returnAt, @Param("updatedAt") LocalDateTime updatedAt);

    // 买家确认上门取件：写 pickup_at/pickup_no/return_at
    int updatePickup(@Param("afterSaleId") Long afterSaleId,
                     @Param("pickupAt") LocalDateTime pickupAt, @Param("pickupNo") String pickupNo,
                     @Param("updatedAt") LocalDateTime updatedAt);

    // 商家补发信息（选填，写 ship_courier/ship_tracking_number/ship_at）
    int updateShipInfo(@Param("afterSaleId") Long afterSaleId,
                       @Param("shipCourier") String shipCourier,
                       @Param("shipTrackingNumber") String shipTrackingNumber,
                       @Param("shipAt") LocalDateTime shipAt, @Param("updatedAt") LocalDateTime updatedAt);

    // 后台审核：更新指定时间戳列（列名由 STATUS_COLUMN_MAP 白名单保证）
    int updateTimestamp(@Param("afterSaleId") Long afterSaleId,
                        @Param("column") String column, @Param("now") LocalDateTime now);

    // 软删除子表
    int softDelete(@Param("afterSaleId") Long afterSaleId, @Param("now") LocalDateTime now);
}
