package ecommerce.order.mapper;

import ecommerce.order.entity.Cart;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 购物车 Mapper（cart 表）。
 */
public interface CartMapper {

    // 查某用户购物车列表（按加购时间倒序）
    List<Cart> listByUser(@Param("userId") Long userId);

    // 查某用户购物车商品总件数（首页角标用，避免拉完整列表）
    int countQuantity(@Param("userId") Long userId);

    // 查某用户购物车里某个商品的行（不存在返回 null；多规格时取第一条，兼容旧数据）
    Cart findByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);

    // 按 SKU 查购物车行（多规格精确匹配）
    Cart findByUserAndSku(@Param("userId") Long userId, @Param("skuId") Long skuId);

    // 新增购物车项
    int insert(Cart cart);

    // 按 id + userId 更新（数量 + 快照字段 + 更新时间）
    int update(Cart cart);

    // 移除某个商品行
    int deleteByProduct(@Param("userId") Long userId, @Param("productId") Long productId);

    // 按 SKU 移除（多规格精确删除）
    int deleteBySku(@Param("userId") Long userId, @Param("skuId") Long skuId);

    // 批量移除（productIds 数组）
    int deleteByProducts(@Param("userId") Long userId, @Param("productIds") List<Long> productIds);

    // 批量移除（skuIds 数组）
    int deleteBySkus(@Param("userId") Long userId, @Param("skuIds") List<Long> skuIds);

    // 清空购物车
    int clear(@Param("userId") Long userId);
}