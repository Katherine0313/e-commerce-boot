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
    AfterSaleExchangeReturn findByAfterSaleId(@Param("afterSaleId") Long afterSaleId, @Param("shopId") Long shopId);

    // 用户提交退货方式：写 return_way/return_time_slot/return_at
    int updateReturnInfo(@Param("afterSaleId") Long afterSaleId, @Param("shopId") Long shopId,
                         @Param("returnWay") String returnWay, @Param("returnTimeSlot") String returnTimeSlot,
                         @Param("returnAt") LocalDateTime returnAt, @Param("updatedAt") LocalDateTime updatedAt);
}
