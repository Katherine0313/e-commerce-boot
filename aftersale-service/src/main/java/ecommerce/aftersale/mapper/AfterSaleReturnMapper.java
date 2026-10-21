package ecommerce.aftersale.mapper;

import ecommerce.aftersale.entity.AfterSaleReturn;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 退货退款子表 Mapper（after_sale_returns 表）。
 */
public interface AfterSaleReturnMapper {

    // 插入占位行（创建售后时写，时间戳/退货字段先为 NULL）
    int insertPlaceholder(AfterSaleReturn afterSaleReturn);

    // 按售后单查子表（不含软删除）
    AfterSaleReturn findByAfterSaleId(@Param("afterSaleId") Long afterSaleId, @Param("shopId") Long shopId);

    // 用户提交退货方式：写 return_way/return_time_slot/return_at
    int updateReturnInfo(@Param("afterSaleId") Long afterSaleId, @Param("shopId") Long shopId,
                         @Param("returnWay") String returnWay, @Param("returnTimeSlot") String returnTimeSlot,
                         @Param("returnAt") LocalDateTime returnAt, @Param("updatedAt") LocalDateTime updatedAt);

    // 回填退货物流：写 return_courier/return_tracking_number/return_at
    int updateTracking(@Param("afterSaleId") Long afterSaleId, @Param("shopId") Long shopId,
                       @Param("courier") String courier, @Param("trackingNumber") String trackingNumber,
                       @Param("returnAt") LocalDateTime returnAt, @Param("updatedAt") LocalDateTime updatedAt);

    // 后台审核：更新指定时间戳列（列名由 STATUS_COLUMN_MAP 白名单保证）
    int updateTimestamp(@Param("afterSaleId") Long afterSaleId, @Param("shopId") Long shopId,
                        @Param("column") String column, @Param("now") LocalDateTime now);

    // 软删除子表
    int softDelete(@Param("afterSaleId") Long afterSaleId, @Param("shopId") Long shopId, @Param("now") LocalDateTime now);
}
