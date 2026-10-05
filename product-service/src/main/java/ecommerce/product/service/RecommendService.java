package ecommerce.product.service;

import ecommerce.product.vo.ProductVO;

import java.util.List;

/**
 * 商品推荐服务：调 Python 推荐服务拿商品 ID，再查商品表拼 VO。
 */
public interface RecommendService {

    /**
     * 猜你喜欢推荐列表；未登录/未下单走销量热门，Python 不可用走商品列表兜底
     */
    List<ProductVO> recommend(Integer limit);
}
