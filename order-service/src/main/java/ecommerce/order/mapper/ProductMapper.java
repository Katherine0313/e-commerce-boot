package ecommerce.order.mapper;

import ecommerce.order.entity.Product;
import org.apache.ibatis.annotations.Param;

/**
 * 商品 Mapper（order-service 内查 products 表的精简版）。
 * <p>
 * 与 product-service 共用同一个 MySQL 库，加购时从这里拿商品快照（同库直接查表）。
 */
public interface ProductMapper {

    // 查商品快照（name/image/sales_price/description/stock），加购/下单时校验商品存在 + 生成快照
    Product findById(@Param("id") Long id, @Param("shopId") Long shopId);

    // 扣库存：带 shop_id 多租户过滤 + stock >= quantity 条件（乐观锁兜底防超卖），返回 0 表示库存不足
    int deductStock(@Param("id") Long id, @Param("quantity") Integer quantity, @Param("shopId") Long shopId);

    // 恢复库存（取消/超时关闭时加回）
    int restoreStock(@Param("id") Long id, @Param("quantity") Integer quantity, @Param("shopId") Long shopId);
}
