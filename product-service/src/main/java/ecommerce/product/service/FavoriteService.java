package ecommerce.product.service;

import ecommerce.product.vo.FavoriteVO;

import java.util.List;

/**
 * 商品收藏 Service。
 * <p>
 * 收藏是账号级数据：用户 ID / 店铺 ID 统一从 UserContext（JWT）取，不信任前端传参。
 */
public interface FavoriteService {

    /** 当前用户收藏列表（按收藏时间倒序，含已下架商品） */
    List<FavoriteVO> list();

    /** 是否已收藏该商品（详情页回显） */
    boolean check(Long productId);

    /** 收藏商品：商品必须存在且属于本店；重复收藏幂等 */
    void add(Long productId);

    /** 取消收藏：不存在也按成功处理（幂等） */
    void remove(Long productId);
}
