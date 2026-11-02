package ecommerce.order.mapper;

import ecommerce.order.entity.ProductReview;
import ecommerce.order.vo.AdminReviewVO;
import ecommerce.order.vo.ProductSnapshotVO;
import ecommerce.order.vo.ReviewDetailVO;
import ecommerce.order.vo.ReviewSummaryVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品评价 Mapper（product_reviews 表）。
 * <p>
 * 买家端提交评价与后台管理/C 端查询**共用这一个 Mapper**（与 OrderMapper 同时承载
 * listAdmin / countAdmin 的做法一致）。买家端的老方法（insert / findByOrderId）保持原样不动。
 */
public interface ProductReviewMapper {

    // ===== 买家端（原有，勿改） =====

    // 插入评价（回填自增 id）
    int insert(ProductReview review);

    // 查某订单的评价（不含软删除）；用于「是否已评价」标记与防重复提交
    ProductReview findByOrderId(@Param("orderId") Long orderId);

    // ===== 后台管理 =====

    // 后台详情（含订单号 / 评价人 / 虚拟评价创建人）
    AdminReviewVO findAdminById(@Param("id") Long id);

    // 后台列表（分页 + 筛选）
    List<AdminReviewVO> listAdmin(@Param("offset") int offset, @Param("pageSize") int pageSize,
                                  @Param("keyword") String keyword, @Param("orderNo") String orderNo,
                                  @Param("rating") Integer rating, @Param("isVirtual") Integer isVirtual,
                                  @Param("status") Integer status,
                                  @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 后台列表总数（与 listAdmin 同筛选条件）
    long countAdmin(@Param("keyword") String keyword, @Param("orderNo") String orderNo,
                    @Param("rating") Integer rating, @Param("isVirtual") Integer isVirtual,
                    @Param("status") Integer status,
                    @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 插入虚拟评价（order_id / user_id 固定 0，is_virtual=1）
    int insertVirtual(ProductReview review);

    // 后台编辑（仅虚拟评价走这条；真实评价由 Service 层拦掉）
    int updateByAdmin(ProductReview review);

    // 写商家回复（一条评价只保留一条回复，再次回复覆盖）
    int updateReply(@Param("id") Long id, @Param("reply") String reply,
                    @Param("replyTime") LocalDateTime replyTime);

    // 切换显示状态（status：1 显示 0 隐藏）
    int updateStatus(@Param("id") Long id, @Param("status") Integer status,
                      @Param("updatedAt") LocalDateTime updatedAt);

    // 逻辑删除（is_deleted=1）
    int softDelete(@Param("id") Long id, @Param("updatedAt") LocalDateTime updatedAt);

    // 取商品快照（建虚拟评价时用，不信任前端传的商品名/图）
    ProductSnapshotVO findProductSnapshot(@Param("productId") Long productId);

    // ===== C 端 =====

    // C 端评价列表（只返回可见且未删除的）
    List<ReviewDetailVO> listByProduct(@Param("productId") Long productId,
                                       @Param("offset") int offset, @Param("pageSize") int pageSize);

    // C 端评价条数
    long countByProduct(@Param("productId") Long productId);

    // C 端评价统计（总数/平均分/好评率/星级分布）
    ReviewSummaryVO summaryByProduct(@Param("productId") Long productId);
}
