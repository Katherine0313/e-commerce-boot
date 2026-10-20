package ecommerce.shop.service;

import ecommerce.shop.vo.SandboxVO;
import ecommerce.shop.vo.ShopVO;

/**
 * 店铺 Service：开沙箱 + 当前店铺信息。
 */
public interface ShopService {
    // 一键体验：开沙箱店（克隆模板店数据 + 签发沙箱店 admin 令牌）
    SandboxVO createSandbox();

    // 当前店铺信息（按 admin 令牌的 shopId 查）
    ShopVO getCurrentShop();
}
