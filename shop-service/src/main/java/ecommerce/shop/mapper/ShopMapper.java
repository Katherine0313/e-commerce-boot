package ecommerce.shop.mapper;

import ecommerce.shop.entity.Admin;
import ecommerce.shop.entity.Shop;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 店铺 Mapper（shops 表 + 15 张业务表克隆）。
 */
public interface ShopMapper {

    // 插入沙箱店（自增 id 回填）
    int insertShop(Shop shop);

    // 按 id 查店铺
    Shop findById(@Param("id") Long id);

    // ===== 沙箱复用：同一访客 24 小时内重进时复用原店，不再克隆 =====

    // 按访客标识查沙箱店（唯一键，最多一条；是否可复用由 Service 判断过期时间）
    Shop findSandboxByVisitorKey(@Param("visitorKey") String visitorKey);

    // 解绑某个店上的访客标识（过期店/脏数据店，给新店让出唯一键）
    int clearVisitorKeyByShopId(@Param("shopId") Long shopId);

    // ===== 克隆模板店（shop_id=1）数据到沙箱店：id 和外键列加 offset，shop_id 换新 =====
    void cloneCategories(@Param("shopId") Long shopId, @Param("offset") Long offset);
    void cloneTags(@Param("shopId") Long shopId, @Param("offset") Long offset);
    void cloneProducts(@Param("shopId") Long shopId, @Param("offset") Long offset);
    void cloneStock(@Param("shopId") Long shopId, @Param("offset") Long offset);
    void cloneAdmin(@Param("shopId") Long shopId, @Param("offset") Long offset,
                    @Param("now") LocalDateTime now);
    void cloneUser(@Param("shopId") Long shopId, @Param("offset") Long offset);
    void cloneAddress(@Param("shopId") Long shopId, @Param("offset") Long offset);
    void cloneOrders(@Param("shopId") Long shopId, @Param("offset") Long offset);
    void cloneOrderItems(@Param("shopId") Long shopId, @Param("offset") Long offset);
    void cloneCart(@Param("shopId") Long shopId, @Param("offset") Long offset);
    void cloneAfterSales(@Param("shopId") Long shopId, @Param("offset") Long offset);
    void cloneAfterSaleItems(@Param("shopId") Long shopId, @Param("offset") Long offset);
    void cloneAfterSaleReturns(@Param("shopId") Long shopId, @Param("offset") Long offset);
    void cloneAfterSaleExchangeReturns(@Param("shopId") Long shopId, @Param("offset") Long offset);
    void cloneAfterSaleOnlyRefunds(@Param("shopId") Long shopId, @Param("offset") Long offset);

    // 查沙箱店管理员（克隆出来的 admin，取最小 id）
    Admin findSandboxAdmin(@Param("shopId") Long shopId);

    // ===== 过期沙箱回收：查到期店铺，再按外键顺序逐表删除 =====
    List<Long> findExpiredSandboxIds(@Param("now") LocalDateTime now);

    void deleteAfterSaleExchangeReturns(@Param("shopId") Long shopId);
    void deleteAfterSaleOnlyRefunds(@Param("shopId") Long shopId);
    void deleteAfterSaleReturns(@Param("shopId") Long shopId);
    void deleteAfterSaleItems(@Param("shopId") Long shopId);
    void deleteAfterSales(@Param("shopId") Long shopId);
    void deleteOrderItems(@Param("shopId") Long shopId);
    void deleteOrders(@Param("shopId") Long shopId);
    void deleteCart(@Param("shopId") Long shopId);
    void deleteFeedback(@Param("shopId") Long shopId);
    void deleteProductFavorites(@Param("shopId") Long shopId);
    void deleteProductReviews(@Param("shopId") Long shopId);
    void deleteAddress(@Param("shopId") Long shopId);
    void deleteUser(@Param("shopId") Long shopId);
    void deleteAdmin(@Param("shopId") Long shopId);
    void deleteStock(@Param("shopId") Long shopId);
    void deleteProducts(@Param("shopId") Long shopId);
    void deleteTags(@Param("shopId") Long shopId);
    void deleteCategories(@Param("shopId") Long shopId);
    void deleteShop(@Param("shopId") Long shopId);
}
