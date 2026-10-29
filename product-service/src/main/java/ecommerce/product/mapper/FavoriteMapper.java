package ecommerce.product.mapper;

import ecommerce.product.vo.FavoriteVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品收藏 Mapper（product_favorites 表）。
 */
public interface FavoriteMapper {

    // 收藏：唯一键冲突时忽略，重复点击不会报错也不会产生第二条
    int insert(@Param("userId") Long userId,
               @Param("productId") Long productId, @Param("createdAt") LocalDateTime createdAt);

    // 取消收藏：不存在的记录删除 0 行，接口仍按成功处理（幂等）
    int delete(@Param("userId") Long userId,
               @Param("productId") Long productId);

    // 是否已收藏
    boolean exists(@Param("userId") Long userId,
                   @Param("productId") Long productId);

    // 当前用户收藏列表：join 商品取展示字段，按收藏时间倒序（含已下架商品）
    List<FavoriteVO> listByUser(@Param("userId") Long userId);
}
