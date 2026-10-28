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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

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
    /** 复用门槛 / 令牌有效下限：店铺剩余不足 60 秒就不复用，直接开新店 */
    private static final long SANDBOX_MIN_TOKEN_SECONDS = 60;
    private static final String SANDBOX_SHOP_NAME = "杰米小店";
    /** 访客标识：标准 UUID 形状（前端生成 v4，后端统一小写） */
    private static final Pattern VISITOR_KEY_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final ShopMapper shopMapper;
    private final JwtUtil jwtUtil;
    /** 用编程式事务把「插店 + 克隆」包成一个事务，冲突时能先回滚再做一次独立读 */
    private final TransactionTemplate transactionTemplate;

    public ShopServiceImpl(ShopMapper shopMapper, JwtUtil jwtUtil,
                           PlatformTransactionManager transactionManager) {
        this.shopMapper = shopMapper;
        this.jwtUtil = jwtUtil;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public SandboxVO createSandbox(String rawVisitorKey) {
        String visitorKey = normalizeVisitorKey(rawVisitorKey);
        try {
            return transactionTemplate.execute(status -> doCreateSandbox(visitorKey));
        } catch (DuplicateKeyException e) {
            // 同一 visitorKey 并发请求：本事务已回滚，新连接上读对方已提交的店铺并复用
            return reuseAfterConflict(visitorKey, e);
        }
    }

    /** 在一个事务内完成：复用判断 / 插店 / 克隆 15 张表 / 签令牌 */
    private SandboxVO doCreateSandbox(String visitorKey) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime minExpiresAt = now.plusSeconds(SANDBOX_MIN_TOKEN_SECONDS);

        // 同一浏览器 24h 内重进：命中未过期沙箱 → 不插店、不克隆，只重签原 admin 的令牌
        if (visitorKey != null) {
            Shop holder = shopMapper.findSandboxByVisitorKey(visitorKey);
            if (holder != null) {
                // 剩余不足 60 秒就不复用，避免签出比店铺先过期的令牌
                boolean stillValid = holder.getExpiresAt().isAfter(minExpiresAt);
                Admin holderAdmin = stillValid ? shopMapper.findSandboxAdmin(holder.getId()) : null;
                if (holderAdmin != null) {
                    return toSandboxVO(holder, holderAdmin, true);
                }
                // 已过期/即将过期，或极端脏数据缺 admin：解绑该键后按新店处理
                if (stillValid) {
                    log.warn("沙箱店 {} 缺少 admin，解绑 visitorKey 后重建", holder.getId());
                }
                shopMapper.clearVisitorKeyByShopId(holder.getId());
            }
        }

        Shop shop = Shop.builder()
                .name(SANDBOX_SHOP_NAME)
                .isTemplate(0)
                .createTime(now)
                .expiresAt(now.plusHours(SANDBOX_TTL_HOURS))
                .visitorKey(visitorKey)
                .build();
        shopMapper.insertShop(shop);
        Long shopId = shop.getId();
        long offset = shopId * ID_BLOCK;

        // 克隆模板店数据（顺序：被依赖的表先克隆）
        shopMapper.cloneCategories(shopId, offset);
        shopMapper.cloneTags(shopId, offset);
        shopMapper.cloneProducts(shopId, offset);
        shopMapper.cloneStock(shopId, offset);
        shopMapper.cloneAdmin(shopId, offset, now);
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
        return toSandboxVO(shop, admin, false);
    }

    /** 校验并归一化访客标识：缺失/空白 → null（旧客户端保持每次新建）；格式非法 → 400 */
    private String normalizeVisitorKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        if (!VISITOR_KEY_PATTERN.matcher(key).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "访客标识格式不正确");
        }
        return key;
    }

    /** 并发兜底：冲突事务已回滚，这里在独立连接上读到的是对方已提交的店铺 */
    private SandboxVO reuseAfterConflict(String visitorKey, DuplicateKeyException cause) {
        if (visitorKey == null) {
            throw cause;
        }
        Shop winner = shopMapper.findSandboxByVisitorKey(visitorKey);
        Admin admin = winner == null ? null : shopMapper.findSandboxAdmin(winner.getId());
        if (admin == null) {
            throw new BusinessException(HttpStatus.CONFLICT, "体验店铺正在开通，请稍后重试");
        }
        return toSandboxVO(winner, admin, true);
    }

    /** 统一签令牌：TTL = 签名时刻到店铺过期的剩余秒数，最少 60 秒 */
    private SandboxVO toSandboxVO(Shop shop, Admin admin, boolean restored) {
        long ttlSeconds = Math.max(SANDBOX_MIN_TOKEN_SECONDS,
                Duration.between(LocalDateTime.now(), shop.getExpiresAt()).getSeconds());
        String token = jwtUtil.generateAdminToken(
                admin.getId(), admin.getUsername(), admin.getRole(), shop.getId(), ttlSeconds);

        return SandboxVO.builder()
                .token(token)
                .restored(restored)
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
