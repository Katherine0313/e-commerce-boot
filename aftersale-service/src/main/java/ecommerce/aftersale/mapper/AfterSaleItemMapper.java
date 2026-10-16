package ecommerce.aftersale.mapper;

import ecommerce.aftersale.entity.AfterSaleItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 售后明细 Mapper（after_sale_items 表）。
 */
public interface AfterSaleItemMapper {

    // 批量插入售后明细
    int insertBatch(@Param("items") List<AfterSaleItem> items);

    // 查某售后单的明细（不含软删除）
    List<AfterSaleItem> listByAfterSaleId(@Param("afterSaleId") Long afterSaleId, @Param("shopId") Long shopId);
}
