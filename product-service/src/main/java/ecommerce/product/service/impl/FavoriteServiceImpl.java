package ecommerce.product.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.product.mapper.FavoriteMapper;
import ecommerce.product.mapper.ProductMapper;
import ecommerce.product.service.FavoriteService;
import ecommerce.product.vo.FavoriteVO;
import ecommerce.utils.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品收藏 Service 实现。
 */
@Service
public class FavoriteServiceImpl implements FavoriteService {

    /** 模板店（杰米小店）：多租户沙箱接入前，令牌没有 shopId 时回退到 1，与商品浏览口径一致 */
    private static final Long TEMPLATE_SHOP_ID = 1L;

    private final FavoriteMapper favoriteMapper;
    private final ProductMapper productMapper;

    public FavoriteServiceImpl(FavoriteMapper favoriteMapper, ProductMapper productMapper) {
        this.favoriteMapper = favoriteMapper;
        this.productMapper = productMapper;
    }

    @Override
    public List<FavoriteVO> list() {
        return favoriteMapper.listByUser(currentShopId(), currentUserId());
    }

    @Override
    public boolean check(Long productId) {
        return favoriteMapper.exists(currentShopId(), currentUserId(), productId);
    }

    @Override
    public void add(Long productId) {
        Long shopId = currentShopId();
        Long userId = currentUserId();
        // 商品必须存在且属于本店，避免收藏脏数据；下架商品允许收藏（收藏关系保留）
        if (productMapper.findById(productId, shopId) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        // createdAt 用 JVM 时间，不用 MySQL NOW()（数据库时区 UTC 会差 8 小时）
        favoriteMapper.insert(shopId, userId, productId, LocalDateTime.now());
    }

    @Override
    public void remove(Long productId) {
        favoriteMapper.delete(currentShopId(), currentUserId(), productId);
    }

    /** 当前店铺：user 令牌带 shopId；缺失时回退模板店（与 ProductServiceImpl 的商品浏览口径一致） */
    private Long currentShopId() {
        Long shopId = UserContext.getShopId();
        return shopId == null ? TEMPLATE_SHOP_ID : shopId;
    }

    /** 当前用户：/api/favorites 不在免登录白名单，拦截器已保证有值，这里再兜一层 */
    private Long currentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        return userId;
    }
}
