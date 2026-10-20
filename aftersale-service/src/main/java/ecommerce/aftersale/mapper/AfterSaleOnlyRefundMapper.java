package ecommerce.aftersale.mapper;

import ecommerce.aftersale.entity.AfterSaleOnlyRefund;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 仅退款子表 Mapper（after_sale_only_refunds 表）。
 */
public interface AfterSaleOnlyRefundMapper {

    // 插入占位行（创建售后时写，时间戳先为 NULL）
    int insertPlaceholder(AfterSaleOnlyRefund onlyRefund);

    // 按售后单查子表（不含软删除）
    AfterSaleOnlyRefund findByAfterSaleId(@Param("afterSaleId") Long afterSaleId, @Param("shopId") Long shopId);

    // 后台审核：更新指定时间戳列（列名由 STATUS_COLUMN_MAP 白名单保证）
    int updateTimestamp(@Param("afterSaleId") Long afterSaleId, @Param("shopId") Long shopId,
                        @Param("column") String column, @Param("now") LocalDateTime now);

    // 软删除子表
    int softDelete(@Param("afterSaleId") Long afterSaleId, @Param("shopId") Long shopId, @Param("now") LocalDateTime now);
}
