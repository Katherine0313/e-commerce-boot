package ecommerce.product.service;

import ecommerce.product.vo.BannerVO;

import java.util.List;

/**
 * Banner C 端服务（小程序 / PC 商城轮播，免登录）。
 */
public interface BannerService {

    /** 某个位置上所有启用的 Banner，按 sort_order 升序 */
    List<BannerVO> listEnabled(Integer position);

    /** 点击次数 +1 */
    void browse(Long id);
}
