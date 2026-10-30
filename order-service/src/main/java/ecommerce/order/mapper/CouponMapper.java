package ecommerce.order.mapper;

import ecommerce.order.entity.CouponScope;
import ecommerce.order.entity.CouponTemplate;
import ecommerce.order.entity.UserCoupon;
import ecommerce.order.vo.CouponRecordVO;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 优惠券 Mapper（coupon_template / coupon_scope / user_coupon）。
 * <p>
 * 时间条件统一由 Java 传入 now，不使用 MySQL NOW()（服务器时区 UTC）。
 */
public interface CouponMapper {

    // ==================== C 端 ====================

    // 领券中心：未删除、未停止、now 在领取窗口内、还有剩余
    List<CouponTemplate> listCenter(@Param("now") LocalDateTime now);

    // 某用户已领取的模板 id（领券中心标记已领取）
    List<Long> listClaimedTemplateIds(@Param("userId") Long userId);

    CouponTemplate findTemplateById(@Param("id") Long id);

    List<CouponScope> listScopesByTemplateIds(@Param("templateIds") List<Long> templateIds);

    UserCoupon findUserCouponById(@Param("id") Long id);

    UserCoupon findUserCouponByUserAndTemplate(@Param("userId") Long userId,
                                               @Param("templateId") Long templateId);

    int insertUserCoupon(UserCoupon userCoupon);

    // 原子扣减剩余量，返回 0 表示已领完
    int decreaseRemain(@Param("templateId") Long templateId);

    // 回补剩余量（发放时唯一键冲突等异常场景）
    int increaseRemain(@Param("templateId") Long templateId);

    // 用户全部券（状态/过期在 Java 里推导）
    List<UserCoupon> listUserCoupons(@Param("userId") Long userId);

    // 可用券：未使用且未过期
    List<UserCoupon> listAvailableUserCoupons(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    List<UserCoupon> listUserCouponsByOrder(@Param("orderId") Long orderId);

    // 下单锁定：UNUSED -> LOCKED（带状态条件，防并发重复使用）
    int lockCoupon(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    // 锁定后回填订单 id
    int bindOrder(@Param("id") Long id, @Param("orderId") Long orderId);

    // 支付成功：LOCKED -> USED
    int markUsedByOrder(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);

    // 取消/超时：LOCKED -> UNUSED
    int releaseLockedByOrder(@Param("orderId") Long orderId);

    // 整单退款：USED -> UNUSED（仅未过期）
    int releaseUsedByOrder(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);

    // 商品所属分类及父分类（判断分类券适用范围）
    List<Map<String, Object>> listProductCategories(@Param("productIds") List<Long> productIds);

    // ==================== 后台管理 ====================

    List<CouponTemplate> listAdmin(@Param("keyword") String keyword, @Param("status") String status,
                                   @Param("now") LocalDateTime now,
                                   @Param("offset") int offset, @Param("pageSize") int pageSize);

    long countAdmin(@Param("keyword") String keyword, @Param("status") String status,
                    @Param("now") LocalDateTime now);

    List<CouponScope> listScopes(@Param("templateId") Long templateId);

    int insertTemplate(CouponTemplate template);

    int updateTemplate(CouponTemplate template);

    int softDelete(@Param("id") Long id, @Param("now") LocalDateTime now);

    int stopNow(@Param("id") Long id, @Param("now") LocalDateTime now);

    int deleteScopes(@Param("templateId") Long templateId);

    int insertScopes(@Param("scopes") List<CouponScope> scopes);

    long countIssued(@Param("templateId") Long templateId);

    long countUsed(@Param("templateId") Long templateId);

    BigDecimal sumUsedDiscount(@Param("templateId") Long templateId);

    // 券领取/使用记录（后台查看）
    List<CouponRecordVO> listRecords(@Param("templateId") Long templateId,
                                     @Param("offset") int offset, @Param("pageSize") int pageSize);

    long countRecords(@Param("templateId") Long templateId);

    // 全部有效用户 id（后台全量发券）
    List<Long> listActiveUserIds();
}
