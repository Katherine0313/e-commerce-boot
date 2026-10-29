package ecommerce.order.service;

import ecommerce.order.dto.CreateReviewDTO;
import ecommerce.order.vo.ReviewVO;

/**
 * 商品评价 Service。
 * <p>
 * 评价绑定「已完成」订单：一个订单只允许评一次，当前登录用户从 UserContext 取（单店模式）。
 */
public interface ReviewService {

    /** 提交评价：校验订单归属 + 已完成 + 未评价 → 从订单明细取商品快照落库 */
    ReviewVO submit(Long orderId, CreateReviewDTO dto);
}
