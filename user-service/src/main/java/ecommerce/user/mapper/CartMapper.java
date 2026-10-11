package ecommerce.user.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 购物车 Mapper（user-service 侧，仅用于注销时清购物车）。
 * <p>
 * cart 表归属 order-service；这里直连同库删行，是为了和「置 user.is_deleted=1」共用一个事务
 * （跨服务调用无法保证原子性）。沿用 order-service 直查 products 表的同库直连先例，
 * 将来服务拆库 / 引入 Seata 时再改为跨服务调用。
 */
public interface CartMapper {

    // 删除某用户的全部购物车项
    int deleteByUser(@Param("userId") Long userId);
}
