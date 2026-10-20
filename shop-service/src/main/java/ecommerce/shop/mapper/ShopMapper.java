package ecommerce.shop.mapper;

import ecommerce.shop.entity.Admin;
import ecommerce.shop.entity.Shop;
import org.apache.ibatis.annotations.Param;

/**
 * 店铺 Mapper（shops 表 + 15 张业务表克隆）。
 */
public interface ShopMapper {

    // 插入沙箱店（自增 id 回填）
    int insertShop(Shop shop);

    // 按 id 查店铺
    Shop findById(@Param("id") Long id);

    // ===== 克隆模板店（shop_id=1）数据到沙箱店：id 和外键列加 offset，shop_id 换新 =====
    void cloneCategories(@Param("shopId") Long shopId, @Param("offset") Long offset);
    void cloneTags(@Param("shopId") Long shopId, @Param("offset") Long offset);
    void cloneProducts(@Param("shopId") Long shopId, @Param("offset") Long offset);
    void cloneStock(@Param("shopId") Long shopId, @Param("offset") Long offset);
    void cloneAdmin(@Param("shopId") Long shopId, @Param("offset") Long offset);
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
}
