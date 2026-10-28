package ecommerce.shop.service;

import ecommerce.shop.vo.SandboxVO;
import ecommerce.shop.vo.ShopVO;

/**
 * 店铺 Service：开沙箱 + 当前店铺信息。
 */
public interface ShopService {
    // 一键体验：访客标识命中未过期沙箱则复用，否则克隆模板店数据新开 + 签发 admin 令牌
    SandboxVO createSandbox(String visitorKey);

    // 当前店铺信息（按 admin 令牌的 shopId 查）
    ShopVO getCurrentShop();
}
