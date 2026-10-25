package ecommerce.order.mapper;

import ecommerce.order.entity.Cart;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 购物车 Mapper（cart 表）。
 */
public interface CartMapper {

    // 查某用户购物车列表（按加购时间倒序）
    List<Cart> listByUser(@Param("userId") Long userId, @Param("shopId") Long shopId);

    // 查某用户购物车里某个商品的行（不存在返回 null）
    Cart findByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId,
                              @Param("shopId") Long shopId);

    // 新增购物车项
    int insert(Cart cart);

    // 按 id + userId + shopId 更新（数量 + 快照字段 + 更新时间）
    int update(Cart cart);

    // 移除某个商品
    int deleteByProduct(@Param("userId") Long userId, @Param("productId") Long productId,
                        @Param("shopId") Long shopId);

    // 批量移除（productIds 数组）
    int deleteByProducts(@Param("userId") Long userId, @Param("productIds") List<Long> productIds,
                         @Param("shopId") Long shopId);

    // 清空购物车
    int clear(@Param("userId") Long userId, @Param("shopId") Long shopId);
}