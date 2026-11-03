package ecommerce.aftersale.mapper;

import ecommerce.aftersale.entity.AfterSaleLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 售后变更流水 Mapper。
 */
@Mapper
public interface AfterSaleLogMapper {

    /** 写入一条变更流水 */
    int insert(AfterSaleLog log);

    /** 按售后单 ID 查变更流水（按时间正序） */
    List<AfterSaleLog> listByAfterSaleId(@Param("afterSaleId") Long afterSaleId);
}
