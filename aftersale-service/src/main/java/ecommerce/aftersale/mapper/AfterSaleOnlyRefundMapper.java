package ecommerce.aftersale.mapper;

import ecommerce.aftersale.entity.AfterSaleOnlyRefund;
import org.apache.ibatis.annotations.Param;

/**
 * 仅退款子表 Mapper（after_sale_only_refunds 表）。
 */
public interface AfterSaleOnlyRefundMapper {

    // 插入占位行（创建售后时写，时间戳先为 NULL）
    int insertPlaceholder(AfterSaleOnlyRefund onlyRefund);

    // 按售后单查子表（不含软删除）
    AfterSaleOnlyRefund findByAfterSaleId(@Param("afterSaleId") Long afterSaleId, @Param("shopId") Long shopId);
}
