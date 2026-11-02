package ecommerce.order.service;

import ecommerce.common.PageVO;
import ecommerce.order.dto.CreateReviewDTO;
import ecommerce.order.vo.ReviewDetailVO;
import ecommerce.order.vo.ReviewSummaryVO;
import ecommerce.order.vo.ReviewVO;

/**
 * 商品评价 Service。
 * <p>
 * 评价绑定「已完成」订单：一个订单只允许评一次，当前登录用户从 UserContext 取（单店模式）。
 * <p>
 * 另外提供 C 端只读查询：商品详情页看评价列表与星级统计（只返回 status=1 且未删除的）。
 */
public interface ReviewService {

    /** 提交评价：校验订单归属 + 已完成 + 未评价 → 从订单明细取商品快照落库 */
    ReviewVO submit(Long orderId, CreateReviewDTO dto);

    /** C 端：某商品的评价列表（只含可见评价，手机号昵称打码） */
    PageVO<ReviewDetailVO> listByProduct(Long productId, int page, int pageSize);

    /** C 端：某商品的评价统计（总数/平均分/好评率/星级分布） */
    ReviewSummaryVO summary(Long productId);
}
