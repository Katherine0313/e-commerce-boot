package ecommerce.shop.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.shop.entity.Admin;
import ecommerce.shop.entity.Shop;
import ecommerce.shop.mapper.ShopMapper;
import ecommerce.shop.service.ShopService;
import ecommerce.shop.vo.AdminBriefVO;
import ecommerce.shop.vo.SandboxVO;
import ecommerce.shop.vo.ShopVO;
import ecommerce.utils.JwtUtil;
import ecommerce.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 店铺 Service 实现。
 * <p>
 * 开沙箱照旧 Node：shops 插一行 → 15 张业务表按「id + offset」克隆模板店数据 →
 * 签发克隆出来的沙箱 admin 令牌。全程一个事务，失败回滚。
 */
@Slf4j
@Service
public class ShopServiceImpl implements ShopService {

    /** id 偏移量基数：shopId × 100 万，保证沙箱克隆出的 id 与模板店/其它沙箱永不撞主键 */
    private static final long ID_BLOCK = 1_000_000L;
    /** 沙箱有效期：24 小时 */
    private static final long SANDBOX_TTL_HOURS = 24;
    private static final String SANDBOX_SHOP_NAME = "杰米小店";

    private final ShopMapper shopMapper;
    private final JwtUtil jwtUtil;

    public ShopServiceImpl(ShopMapper shopMapper, JwtUtil jwtUtil) {
        this.shopMapper = shopMapper;
        this.jwtUtil = jwtUtil;
    }

    @Override
    @Transactional
    public SandboxVO createSandbox() {
        LocalDateTime now = LocalDateTime.now();
        Shop shop = Shop.builder()
                .name(SANDBOX_SHOP_NAME)
                .isTemplate(0)
                .createTime(now)
                .expiresAt(now.plusHours(SANDBOX_TTL_HOURS))
                .build();
        shopMapper.insertShop(shop);
        Long shopId = shop.getId();
        long offset = shopId * ID_BLOCK;

        // 克隆模板店数据（顺序：被依赖的表先克隆）
        shopMapper.cloneCategories(shopId, offset);
        shopMapper.cloneTags(shopId, offset);
        shopMapper.cloneProducts(shopId, offset);
        shopMapper.cloneStock(shopId, offset);
        shopMapper.cloneAdmin(shopId, offset);
        shopMapper.cloneUser(shopId, offset);
        shopMapper.cloneAddress(shopId, offset);
        shopMapper.cloneOrders(shopId, offset);
        shopMapper.cloneOrderItems(shopId, offset);
        shopMapper.cloneCart(shopId, offset);
        shopMapper.cloneAfterSales(shopId, offset);
        shopMapper.cloneAfterSaleItems(shopId, offset);
        shopMapper.cloneAfterSaleReturns(shopId, offset);
        shopMapper.cloneAfterSaleExchangeReturns(shopId, offset);
        shopMapper.cloneAfterSaleOnlyRefunds(shopId, offset);

        Admin admin = shopMapper.findSandboxAdmin(shopId);
        if (admin == null) {
            throw new BusinessException("沙箱管理员克隆失败");
        }
        // 令牌有效期跟店铺走：沙箱 24h 到期后接口返回 401，前台自动清 token 回登录页。
        // 克隆耗时已从剩余时间里扣掉，兜底至少 60 秒（防止极端情况下签出已过期的 token）
        long ttlSeconds = Math.max(60,
                Duration.between(LocalDateTime.now(), shop.getExpiresAt()).getSeconds());
        String token = jwtUtil.generateAdminToken(
                admin.getId(), admin.getUsername(), admin.getRole(), shopId, ttlSeconds);

        return SandboxVO.builder()
                .token(token)
                .user(AdminBriefVO.builder()
                        .id(admin.getId())
                        .username(admin.getUsername())
                        .name(admin.getName())
                        .role(admin.getRole())
                        .build())
                .shop(toVO(shop))
                .build();
    }

    /**
     * 过期沙箱回收：每小时删一批（最多 20 个）到期的沙箱店。
     * <p>
     * 删除顺序按外键依赖：先删售后/订单明细/购物车/地址等子表，再删 orders、user、products
     * 等被引用表，最后删 shops。阈值用 Java 时间传入（MySQL 服务器时区是 UTC，不能用 NOW()）。
     */
    @Scheduled(fixedDelay = 3600_000)
    @Transactional
    public void cleanExpiredSandboxes() {
        List<Long> expiredIds = shopMapper.findExpiredSandboxIds(LocalDateTime.now());
        if (expiredIds.isEmpty()) {
            return;
        }
        for (Long shopId : expiredIds) {
            shopMapper.deleteAfterSaleExchangeReturns(shopId);
            shopMapper.deleteAfterSaleOnlyRefunds(shopId);
            shopMapper.deleteAfterSaleReturns(shopId);
            shopMapper.deleteAfterSaleItems(shopId);
            shopMapper.deleteAfterSales(shopId);
            shopMapper.deleteOrderItems(shopId);
            shopMapper.deleteOrders(shopId);
            shopMapper.deleteCart(shopId);
            shopMapper.deleteFeedback(shopId);
            shopMapper.deleteProductFavorites(shopId);
            shopMapper.deleteProductReviews(shopId);
            shopMapper.deleteAddress(shopId);
            shopMapper.deleteUser(shopId);
            shopMapper.deleteAdmin(shopId);
            shopMapper.deleteStock(shopId);
            shopMapper.deleteProducts(shopId);
            shopMapper.deleteTags(shopId);
            shopMapper.deleteCategories(shopId);
            shopMapper.deleteShop(shopId);
        }
        log.info("过期沙箱回收 {} 个：{}", expiredIds.size(), expiredIds);
    }

    @Override
    public ShopVO getCurrentShop() {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权访问：缺少店铺上下文");
        }
        Shop shop = shopMapper.findById(shopId);
        if (shop == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "店铺不存在");
        }
        return toVO(shop);
    }

    private ShopVO toVO(Shop shop) {
        return ShopVO.builder()
                .id(shop.getId())
                .name(shop.getName())
                .isTemplate(shop.getIsTemplate())
                .createdAt(shop.getCreateTime())
                .expiresAt(shop.getExpiresAt())
                .build();
    }
}
