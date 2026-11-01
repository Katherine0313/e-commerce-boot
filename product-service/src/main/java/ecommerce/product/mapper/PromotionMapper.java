package ecommerce.product.mapper;

import ecommerce.product.entity.Promotion;
import ecommerce.product.entity.PromotionItem;
import ecommerce.product.entity.PromotionRule;
import ecommerce.product.vo.PromotionItemVO;
import ecommerce.product.vo.PromotionStatsVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 促销 Mapper（promotion / promotion_item / promotion_rule / promotion_usage）。
 * <p>
 * 时间条件统一由 Java 传入 now，不使用 MySQL NOW()（服务器时区为 UTC）。
 */
public interface PromotionMapper {

    // ==================== C 端展示 ====================

    // 当前生效的活动：未删除、未提前结束、now 在 [start_time, end_time) 内
    List<Promotion> listActive(@Param("now") LocalDateTime now);

    List<PromotionItem> listItemsByPromotionIds(@Param("promotionIds") List<Long> promotionIds);

    List<PromotionRule> listRulesByPromotionIds(@Param("promotionIds") List<Long> promotionIds);

    // ==================== 后台管理 ====================

    List<Promotion> listAdmin(@Param("keyword") String keyword, @Param("status") String status,
                              @Param("now") LocalDateTime now,
                              @Param("offset") int offset, @Param("pageSize") int pageSize);

    long countAdmin(@Param("keyword") String keyword, @Param("status") String status,
                    @Param("now") LocalDateTime now);

    Promotion findById(@Param("id") Long id);

    // 活动商品（带商品名和当前售价，编辑回填用）
    List<PromotionItemVO> listItemsWithProduct(@Param("promotionId") Long promotionId);

    List<PromotionRule> listRules(@Param("promotionId") Long promotionId);

    /**
     * 该活动产生过的订单数（promotion_usage 去重 order_id）。
     * 有订单的活动不能删：订单里存着活动价和优惠金额快照，删掉活动就没法解释历史订单。
     */
    int countUsageByPromotionId(@Param("promotionId") Long promotionId);

    int insert(Promotion promotion);

    int update(Promotion promotion);

    // 逻辑删除（没有产生过订单的活动允许）
    int softDelete(@Param("id") Long id, @Param("now") LocalDateTime now);

    // 运营提前结束
    int endNow(@Param("id") Long id, @Param("now") LocalDateTime now);

    int deleteItems(@Param("promotionId") Long promotionId);

    int insertItems(@Param("items") List<PromotionItem> items);

    int deleteRules(@Param("promotionId") Long promotionId);

    int insertRules(@Param("rules") List<PromotionRule> rules);

    // 同商品、同类型（直减）活动的时间重叠检查；excludeId 编辑时排除自己
    long countOverlappingDirect(@Param("productIds") List<Long> productIds,
                                @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end,
                                @Param("excludeId") Long excludeId);

    // 商品当前售价：productId -> sales_price（校验活动价必须低于原价）
    List<Map<String, Object>> selectProductPrices(@Param("productIds") List<Long> productIds);

    // 活动效果：使用记录聚合（支付单数按 orders.pay_at 非空统计）
    PromotionStatsVO stats(@Param("promotionId") Long promotionId);
}
