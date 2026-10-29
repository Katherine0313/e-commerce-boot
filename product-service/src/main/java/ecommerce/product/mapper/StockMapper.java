package ecommerce.product.mapper;

import ecommerce.product.entity.Stock;
import ecommerce.product.vo.StockVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 库存预警配置 Mapper（stock 表）。
 */
public interface StockMapper {
    // 预警列表（LEFT JOIN products，含未配置预警的商品）
    List<StockVO> listWithProducts(@Param("search") String search);

    // 按 product_id 查预警配置（upsert 判断用）
    Stock findByProductId(@Param("productId") Long productId);

    // 新增预警配置，自增 id 回填
    int insert(Stock stock);

    // 更新预警配置（动态 set，只更新非 null 字段）
    int update(Stock stock);
}
